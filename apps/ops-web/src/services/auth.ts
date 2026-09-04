import type {
  CaptchaChallengeResponse,
  CsrfResponse,
  LoginAttemptResponse,
  LoginAuditPage,
  ProblemDetails,
  SessionResponse,
  SmsChallengeResponse
} from '@minipay/api-contracts';
import { ApiProblemError, isProblemDetails } from '@minipay/api-client';
import { createRequestId } from '@minipay/shared';

const REQUEST_TIMEOUT_MS = 15_000;

async function fetchWithTimeout(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const controller = new AbortController();
  const timer = globalThis.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    return await fetch(input, { ...init, signal: controller.signal });
  } catch (cause) {
    if ((cause as { name?: string }).name === 'AbortError') {
      throw new Error('请求超时，请检查网络后重试');
    }
    throw cause;
  } finally {
    globalThis.clearTimeout(timer);
  }
}

export async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const method = (init?.method ?? 'GET').toUpperCase();
  const unsafe = !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method);
  const csrf = unsafe ? await getCsrf() : undefined;
  const response = await fetchWithTimeout(url, {
    ...init,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'X-Request-Id': createRequestId(),
      ...(csrf ? { [csrf.headerName]: csrf.token } : {}),
      ...init?.headers
    }
  });
  const payload = (await response.json().catch(() => undefined)) as T | ProblemDetails | undefined;
  if (!response.ok) {
    if (isProblemDetails(payload)) {
      throw new ApiProblemError(payload);
    }
    throw new ApiProblemError({
      type: 'https://docs.minipay.local/problems/unexpected-response',
      title: 'Unexpected response',
      status: response.status,
      code: 'UNEXPECTED_RESPONSE',
      requestId: response.headers.get('X-Request-Id') ?? createRequestId()
    });
  }
  if (payload === undefined) throw new Error('Response body is empty');
  return payload as T;
}

export function getSession(): Promise<SessionResponse> {
  return requestJson<SessionResponse>('/api/v1/ops-session');
}

export function getCsrf(): Promise<CsrfResponse> {
  return requestJson<CsrfResponse>('/api/v1/ops-csrf');
}

async function getIdentityCsrf(): Promise<CsrfResponse> {
  const response = await fetchWithTimeout('/identity/api/v1/csrf', {
    credentials: 'include',
    headers: { Accept: 'application/json', 'X-Request-Id': createRequestId() }
  });
  if (!response.ok) throw new Error('登录安全令牌加载失败，请刷新页面重试');
  return response.json() as Promise<CsrfResponse>;
}

async function requestIdentityJson<T>(url: string, init: RequestInit): Promise<T> {
  const csrf = await getIdentityCsrf();
  const response = await fetchWithTimeout(`/identity${url}`, {
    ...init,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'X-Request-Id': createRequestId(),
      [csrf.headerName]: csrf.token,
      ...init.headers
    }
  });
  const payload = (await response.json().catch(() => undefined)) as T | ProblemDetails | undefined;
  if (!response.ok) {
    if (isProblemDetails(payload)) throw new ApiProblemError(payload);
    throw new Error('身份服务响应异常，请稍后重试');
  }
  if (payload === undefined) throw new Error('身份服务响应为空，请稍后重试');
  return payload as T;
}

export function getLoginAudits(page: number, size: number): Promise<LoginAuditPage> {
  return requestJson<LoginAuditPage>(`/api/v1/login-audits?page=${page}&size=${size}`);
}

/** 创建图形验证码（经 /identity 反向代理访问身份服务）。 */
export function createCaptcha(): Promise<CaptchaChallengeResponse> {
  return requestIdentityJson<CaptchaChallengeResponse>('/api/v1/auth/captchas', { method: 'POST' });
}

/** 下发短信验证码（经 /identity 反向代理访问身份服务）。 */
export function sendSmsChallenge(
  phone: string,
  captchaId: string,
  captchaCode: string
): Promise<SmsChallengeResponse> {
  return requestIdentityJson<SmsChallengeResponse>('/api/v1/auth/sms-challenges', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phone, captchaId, captchaCode })
  });
}

/**
 * 提交运营平台登录（form 编码 + CSRF + Ajax 头）。
 * 身份服务据此返回 JSON `{redirectUrl}`，登录成功后整页跳转完成 OAuth2。
 */
export async function submitLogin(
  endpoint: '/login/password' | '/login/sms',
  fields: Record<string, string>
): Promise<LoginAttemptResponse> {
  const csrf = await getIdentityCsrf();
  const response = await fetchWithTimeout(`/identity${endpoint}`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/x-www-form-urlencoded',
      'X-Requested-With': 'XMLHttpRequest',
      'X-Request-Id': createRequestId(),
      [csrf.headerName]: csrf.token
    },
    body: new URLSearchParams(fields)
  });
  const payload = (await response.json().catch(() => undefined)) as
    | LoginAttemptResponse
    | ProblemDetails
    | undefined;
  if (!response.ok) {
    if (isProblemDetails(payload)) {
      throw new ApiProblemError(payload);
    }
    throw new ApiProblemError({
      type: 'https://docs.minipay.local/problems/unexpected-response',
      title: '登录失败',
      status: response.status,
      code: 'LOGIN_REJECTED',
      requestId: response.headers.get('X-Request-Id') ?? createRequestId()
    });
  }
  if (!payload || typeof payload !== 'object' || !('redirectUrl' in payload)) {
    throw new Error('登录响应无效，请重试');
  }
  return payload as LoginAttemptResponse;
}
