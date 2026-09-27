import { request } from '@umijs/max';
import {
  ApiProblemError,
  isProblemDetails,
  REQUEST_ID_HEADER,
  toApiProblemError
} from '@minipay/api-client';
import type { CsrfResponse, ProblemDetails } from '@minipay/api-contracts';
import { createRequestId } from '@minipay/shared';
import { asRecord, readString } from './parsers';
import type { ConsumerRequestOptions } from '../umi-runtime';

const DEFAULT_TIMEOUT_MS = 15_000;
const CSRF_PATH = '/api/v1/csrf';
const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS', 'TRACE'];

const CSRF_HEADER_FALLBACK = '_csrf';

/* ────────────────────────────── 401 通知 ────────────────────────────── */

type UnauthorizedListener = () => void;
const unauthorizedListeners = new Set<UnauthorizedListener>();

/** 注册「会话已失效」回调；返回取消注册函数。 */
export function onUnauthorized(listener: UnauthorizedListener): () => void {
  unauthorizedListeners.add(listener);
  return () => {
    unauthorizedListeners.delete(listener);
  };
}

function notifyUnauthorized(): void {
  unauthorizedListeners.forEach((listener) => {
    try {
      listener();
    } catch {
      /* 监听器异常不得影响请求错误传播 */
    }
  });
}

/* ────────────────────────────── CSRF ────────────────────────────── */

let csrfToken: CsrfResponse | null = null;
let csrfInflight: Promise<CsrfResponse> | null = null;

/**
 * CSRF 令牌只保存在内存里：不写 localStorage / sessionStorage，也不进 Query 缓存。
 * 登录前、登录后（会话轮换）以及登出后都必须重新取。
 */
export function clearCsrfToken(): void {
  csrfToken = null;
  csrfInflight = null;
}

async function loadCsrfToken(): Promise<CsrfResponse> {
  const response = await transport(CSRF_PATH, { method: 'GET' }, createRequestId());
  const record = asRecord(response.data);
  const headerName = readString(record, 'headerName');
  const token = readString(record, 'token');
  if (!headerName || !token) {
    throw toApiProblemError({
      type: 'about:blank',
      title: '安全令牌加载失败',
      status: 0,
      code: 'CSRF_RESPONSE_INVALID',
      requestId: response.requestId
    });
  }
  return {
    headerName,
    parameterName: readString(record, 'parameterName') ?? CSRF_HEADER_FALLBACK,
    token
  };
}

/** 读取（必要时拉取）当前 CSRF 令牌。 */
export function getCsrfToken(): Promise<CsrfResponse> {
  if (csrfToken) return Promise.resolve(csrfToken);
  if (!csrfInflight) {
    csrfInflight = loadCsrfToken()
      .then((token) => {
        csrfToken = token;
        return token;
      })
      .finally(() => {
        csrfInflight = null;
      });
  }
  return csrfInflight;
}

/* ────────────────────────────── 传输层 ────────────────────────────── */

interface TransportResult {
  data: unknown;
  status: number;
  requestId: string;
}

interface FailureLike {
  code?: string;
  response?: { status?: number; data?: unknown; headers?: unknown };
}

function readResponseRequestId(headers: unknown, fallback: string): string {
  if (!headers || typeof headers !== 'object') return fallback;
  const candidate = headers as { get?: (name: string) => unknown };
  if (typeof candidate.get === 'function') {
    const value = candidate.get(REQUEST_ID_HEADER);
    if (typeof value === 'string' && value) return value;
  }
  const record = headers as Record<string, unknown>;
  const direct = record[REQUEST_ID_HEADER] ?? record['x-request-id'];
  return typeof direct === 'string' && direct ? direct : fallback;
}

/** 按 RFC 9457 归一化响应体；缺少 `requestId` 时回退到响应头或本地请求号。 */
export function problemFromPayload(status: number, body: unknown, fallbackRequestId: string): ApiProblemError {
  if (isProblemDetails(body)) return toApiProblemError(body);

  if (body && typeof body === 'object' && !(body instanceof ArrayBuffer)) {
    const record = asRecord(body);
    const detail = readString(record, 'detail', 'message', 'error_description');
    const title = readString(record, 'title', 'error') ?? detail ?? '请求失败，请稍后重试';
    const code = readString(record, 'code', 'error') ?? (status ? `HTTP_${status}` : 'NETWORK_UNAVAILABLE');
    const problem: ProblemDetails = {
      type: readString(record, 'type') ?? 'about:blank',
      title,
      status: status || 0,
      code,
      requestId: readString(record, 'requestId') ?? fallbackRequestId,
      ...(detail ? { detail } : {})
    };
    return toApiProblemError(problem);
  }

  return toApiProblemError({
    type: 'about:blank',
    title: status ? `请求失败（HTTP ${status}）` : '请求失败',
    status: status || 0,
    code: status ? `HTTP_${status}` : 'NETWORK_UNAVAILABLE',
    requestId: fallbackRequestId
  });
}

/** 把任意请求异常统一映射为 `ApiProblemError`（RFC 9457）。 */
export function toProblemError(error: unknown, fallbackRequestId: string): ApiProblemError {
  const failure: FailureLike = error && typeof error === 'object' ? (error as FailureLike) : {};
  const status = typeof failure.response?.status === 'number' ? failure.response.status : 0;
  const requestId = readResponseRequestId(failure.response?.headers, fallbackRequestId);

  if (status !== 0) return problemFromPayload(status, failure.response?.data, requestId);

  const timedOut = failure.code === 'ECONNABORTED' || failure.code === 'ETIMEDOUT' || failure.code === 'REQUEST_TIMEOUT';
  return toApiProblemError({
    type: 'about:blank',
    title: timedOut ? '请求超时，请重试' : '网络不可用，请检查网络后重试',
    status: 0,
    code: timedOut ? 'REQUEST_TIMEOUT' : 'NETWORK_UNAVAILABLE',
    requestId
  });
}

/** 纯传输：只补 `X-Request-Id` / `Accept` / Cookie，不做 CSRF 注入。 */
async function transport(url: string, options: ConsumerRequestOptions, requestId: string): Promise<TransportResult> {
  try {
    // ⚠️ 必须显式给出 method：调用方（如会话查询）常常省略，而底层请求库会在
    // `method.toUpperCase()` 上直接抛 `Cannot read properties of undefined` ——
    // 2026-09-27 实测：这个异常被归一化成「网络不可用」，表现为 H5 进不去（GET 全废）。
    const method = (options.method ?? 'GET').toUpperCase() as NonNullable<ConsumerRequestOptions['method']>;
    const response = await request(url, {
      ...options,
      method,
      timeout: options.timeout ?? DEFAULT_TIMEOUT_MS,
      withCredentials: true,
      getResponse: true,
      headers: {
        Accept: 'application/json',
        [REQUEST_ID_HEADER]: requestId,
        ...options.headers
      }
    });
    return {
      data: response.data === '' ? undefined : response.data,
      status: response.status,
      requestId: readResponseRequestId(response.headers, requestId)
    };
  } catch (error) {
    // 诊断用：把底层真实异常打到控制台（只含 URL 与错误本身，不含任何凭据）。
    // 否则 transport 的所有失败都会被归一化成「网络不可用」，线上极难定位。
    if (typeof console !== 'undefined') {
      console.error('[transport-failed]', url, error);
    }
    const problem = toProblemError(error, requestId);
    if (problem.problem.status === 401) notifyUnauthorized();
    throw problem;
  }
}

export interface HttpOptions extends ConsumerRequestOptions {
  /** 显式跳过 CSRF 注入（仅用于 CSRF 端点自身）。 */
  skipCsrf?: boolean;
}

/** 统一的业务请求入口：自动补 `X-Request-Id` 与 CSRF 头，并保持 Cookie 会话。 */
export async function httpRequest<TData>(url: string, options: HttpOptions = {}): Promise<TData> {
  const method = (options.method ?? 'GET').toUpperCase();
  const unsafe = !SAFE_METHODS.includes(method);
  const { skipCsrf, ...rest } = options;

  if (!unsafe || skipCsrf) {
    const result = await transport(url, rest, createRequestId());
    return result.data as TData;
  }

  const token = await getCsrfToken();
  try {
    const result = await transport(
      url,
      { ...rest, headers: { ...rest.headers, [token.headerName]: token.token } },
      createRequestId()
    );
    return result.data as TData;
  } catch (error) {
    // 只有「CSRF 校验失败」才允许重放：这类请求在服务端进入业务逻辑之前就被拒绝，
    // 不存在重复扣款风险。其余失败一律上抛，绝不自动重试写操作。
    if (!isCsrfRejection(error)) throw error;
    clearCsrfToken();
    const refreshed = await getCsrfToken();
    const retried = await transport(
      url,
      { ...rest, headers: { ...rest.headers, [refreshed.headerName]: refreshed.token } },
      createRequestId()
    );
    return retried.data as TData;
  }
}

export function isCsrfRejection(error: unknown): boolean {
  if (!(error instanceof ApiProblemError)) return false;
  return error.problem.status === 403 && /csrf/i.test(error.problem.code);
}

/** 拼接查询串，自动跳过空值。 */
export function buildQuery(params: Record<string, string | number | boolean | undefined>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === '') return;
    search.set(key, String(value));
  });
  const query = search.toString();
  return query ? `?${query}` : '';
}
