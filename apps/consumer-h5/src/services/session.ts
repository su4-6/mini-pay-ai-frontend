import type { CsrfResponse } from '@minipay/api-contracts';
import type { ConsumerSession, ConsumerSessionProfile, SmsChallenge } from '../types/consumer';
import { asRecord, readBoolean, readString } from './parsers';
import { clearCsrfToken, getCsrfToken, httpRequest } from './http';

/**
 * 会话与身份（consumer-bff 契约）：
 *   GET    /api/v1/csrf
 *   POST   /api/v1/session/sms   {mobile}
 *   POST   /api/v1/session       {mobile, challengeId, code}
 *   GET    /api/v1/session
 *   DELETE /api/v1/session
 *   POST   /api/v1/pay-password  {paymentPassword}
 */

function parseSession(raw: unknown): ConsumerSession {
  const record = asRecord(raw);
  if (readBoolean(record, 'authenticated') !== true) return { authenticated: false };
  return {
    authenticated: true,
    userId: readString(record, 'userId') ?? '',
    phone: readString(record, 'phone', 'mobile') ?? '',
    displayName: readString(record, 'displayName', 'nickname') ?? 'MiniPay 用户',
    payPasswordSet: readBoolean(record, 'payPasswordSet') ?? false,
    onboardingRequired: readBoolean(record, 'onboardingRequired') ?? false,
    realNameStatus: readString(record, 'realNameStatus') ?? 'UNVERIFIED',
    realNameVerified: readBoolean(record, 'realNameVerified') ?? false
  };
}

function parseChallenge(raw: unknown): SmsChallenge {
  const record = asRecord(raw);
  const challengeId = readString(record, 'challengeId');
  if (!challengeId) {
    throw new Error('验证码响应缺少 challengeId');
  }
  return {
    challengeId,
    expiresAt: readString(record, 'expiresAt') ?? '',
    demoCode: readString(record, 'demoCode')
  };
}

/** 确保 CSRF 令牌已就绪：登录前与登录后都需要先取一次。 */
export function ensureCsrfToken(): Promise<CsrfResponse> {
  return getCsrfToken();
}

export async function fetchSession(): Promise<ConsumerSession> {
  return parseSession(await httpRequest<unknown>('/api/v1/session', { method: 'GET' }));
}

export async function requestSmsChallenge(mobile: string): Promise<SmsChallenge> {
  return parseChallenge(
    await httpRequest<unknown>('/api/v1/session/sms', { method: 'POST', data: { mobile } })
  );
}

export async function createSession(input: {
  mobile: string;
  challengeId: string;
  code: string;
}): Promise<ConsumerSessionProfile> {
  const raw = await httpRequest<unknown>('/api/v1/session', { method: 'POST', data: input });
  // 登录成功后服务端轮换会话，旧 CSRF 令牌随之失效：立即丢弃，下一次写操作重新取。
  clearCsrfToken();
  const session = parseSession(raw);
  if (!session.authenticated) {
    throw new Error('登录响应缺少会话信息');
  }
  return session;
}

export async function deleteSession(): Promise<void> {
  try {
    await httpRequest<unknown>('/api/v1/session', { method: 'DELETE' });
  } finally {
    clearCsrfToken();
  }
}

export async function setPayPassword(paymentPassword: string): Promise<void> {
  await httpRequest<unknown>('/api/v1/pay-password', {
    method: 'POST',
    data: { paymentPassword }
  });
}
