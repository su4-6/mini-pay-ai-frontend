import { createRequestId } from '@minipay/shared';

const REQUEST_TIMEOUT_MS = 15_000;

export interface CaptchaChallenge {
  captchaId: string;
  imageUrl: string;
  expiresAt: string;
}

export interface SmsChallenge {
  challengeId: string;
  maskedPhone?: string;
  expiresAt: string;
  resendAfterSeconds: number;
}

interface CsrfResponse {
  headerName: string;
  token: string;
}

interface LoginResponse {
  redirectUrl: string;
}

interface ProblemResponse {
  code?: string;
  detail?: string;
}

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

async function csrf(): Promise<CsrfResponse> {
  const response = await fetchWithTimeout('/identity/api/v1/csrf', {
    credentials: 'include',
    headers: { Accept: 'application/json', 'X-Request-Id': createRequestId() }
  });
  if (!response.ok) throw new Error('登录安全令牌加载失败，请刷新页面重试');
  return response.json() as Promise<CsrfResponse>;
}

async function identityJson<T>(path: string, init: RequestInit): Promise<T> {
  const token = await csrf();
  const response = await fetchWithTimeout(`/identity${path}`, {
    ...init,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'X-Request-Id': createRequestId(),
      [token.headerName]: token.token,
      ...init.headers
    }
  });
  const payload = await response.json().catch(() => undefined) as T | ProblemResponse | undefined;
  if (!response.ok) {
    throw new Error(payload && typeof payload === 'object' && 'detail' in payload && payload.detail
      ? payload.detail
      : '身份服务响应异常，请稍后重试');
  }
  if (payload === undefined) throw new Error('身份服务响应为空，请稍后重试');
  return payload as T;
}

export function createCaptcha(): Promise<CaptchaChallenge> {
  return identityJson<CaptchaChallenge>('/api/v1/auth/captchas', { method: 'POST' });
}

export function sendSmsChallenge(
  phone: string,
  captchaId: string,
  captchaCode: string
): Promise<SmsChallenge> {
  return identityJson<SmsChallenge>('/api/v1/auth/sms-challenges', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phone, captchaId, captchaCode })
  });
}

export async function loginWithSms(input: {
  phone: string;
  challengeId: string;
  smsCode: string;
}): Promise<LoginResponse> {
  const token = await csrf();
  const response = await fetchWithTimeout('/identity/login/sms', {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/x-www-form-urlencoded',
      'X-Requested-With': 'XMLHttpRequest',
      'X-Request-Id': createRequestId(),
      [token.headerName]: token.token
    },
    body: new URLSearchParams(input)
  });
  const payload = await response.json().catch(() => undefined) as LoginResponse | ProblemResponse | undefined;
  if (!response.ok) {
    throw new Error(payload && 'detail' in payload && payload.detail
      ? payload.detail
      : '登录失败，请检查手机号或短信验证码');
  }
  if (!payload || !('redirectUrl' in payload)) throw new Error('登录响应无效，请重试');
  return payload;
}

export async function loginWithPassword(input: {
  phone: string;
  password: string;
  captchaId: string;
  captchaCode: string;
}): Promise<LoginResponse> {
  const token = await csrf();
  const response = await fetchWithTimeout('/identity/login/password', {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/x-www-form-urlencoded',
      'X-Requested-With': 'XMLHttpRequest',
      'X-Request-Id': createRequestId(),
      [token.headerName]: token.token
    },
    body: new URLSearchParams(input)
  });
  const payload = await response.json().catch(() => undefined) as LoginResponse | ProblemResponse | undefined;
  if (!response.ok) {
    throw new Error(payload && 'detail' in payload && payload.detail
      ? payload.detail
      : '登录失败，请检查手机号、密码或图形验证码');
  }
  if (!payload || !('redirectUrl' in payload)) throw new Error('登录响应无效，请重试');
  return payload;
}

/** Submits Spring Security logout with the Admin BFF CSRF form token. */
export async function submitAdminLogout(): Promise<void> {
  const response = await fetchWithTimeout('/api/v1/csrf', {
    credentials: 'include',
    headers: { Accept: 'application/json', 'X-Request-Id': createRequestId() }
  });
  if (!response.ok) throw new Error('退出安全令牌加载失败，请刷新页面重试');
  const token = await response.json() as CsrfResponse & { parameterName: string };
  const form = document.createElement('form');
  form.method = 'post';
  form.action = '/logout';
  form.hidden = true;
  const input = document.createElement('input');
  input.type = 'hidden';
  input.name = token.parameterName;
  input.value = token.token;
  form.appendChild(input);
  document.body.appendChild(form);
  form.submit();
}
