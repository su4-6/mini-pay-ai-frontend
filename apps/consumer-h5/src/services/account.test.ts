import { beforeEach, describe, expect, it, vi } from 'vitest';

const { httpRequestMock } = vi.hoisted(() => ({ httpRequestMock: vi.fn() }));

vi.mock('./http', () => ({ httpRequest: httpRequestMock }));

import {
  changePaymentPassword,
  completeOnboarding,
  confirmPhoneChange,
  updateProfile,
  verifyPaymentPasswordChange
} from './account';

interface RecordedCall {
  url: string;
  options: {
    method?: string;
    headers?: Record<string, string>;
    data?: Record<string, unknown>;
  };
}

function callAt(index: number): RecordedCall {
  const call = httpRequestMock.mock.calls[index];
  if (!call) throw new Error(`缺少第 ${index} 次请求记录`);
  return { url: String(call[0]), options: (call[1] ?? {}) as RecordedCall['options'] };
}

beforeEach(() => {
  httpRequestMock.mockReset();
});

describe('资料与账户安全契约', () => {
  it('资料更新携带版本号，首次引导使用幂等请求', async () => {
    httpRequestMock.mockResolvedValueOnce({ userId: 'user-1', nickname: '新昵称', version: 4 });
    await updateProfile('新昵称', 3);
    httpRequestMock.mockResolvedValueOnce(undefined);
    await completeOnboarding('新昵称');

    expect(callAt(0)).toMatchObject({
      url: '/api/v1/users/me',
      options: { method: 'PATCH', data: { nickname: '新昵称', version: 3 } }
    });
    expect(callAt(1).url).toBe('/api/v1/users/me/onboarding');
    expect(callAt(1).options.headers?.['Idempotency-Key']).toEqual(expect.any(String));
  });

  it('修改手机号以挑战编号和验证码确认，不传登录或支付密码', async () => {
    httpRequestMock.mockResolvedValueOnce(undefined);
    await confirmPhoneChange('13900000000', 'challenge-1', '123456');

    expect(callAt(0).options.data).toEqual({
      mobile: '13900000000',
      challengeId: 'challenge-1',
      code: '123456'
    });
    expect(callAt(0).options.data).not.toHaveProperty('password');
  });

  it('支付密码修改分成验证码换验证令牌、再提交新密码两步', async () => {
    httpRequestMock.mockResolvedValueOnce({ verificationToken: 'verify-once-1' });
    const token = await verifyPaymentPasswordChange('challenge/1', '123456');
    httpRequestMock.mockResolvedValueOnce(undefined);
    await changePaymentPassword(token, '246810');

    expect(callAt(0)).toMatchObject({
      url: '/api/v1/users/me/payment-password-change-challenges/challenge%2F1/verifications',
      options: { method: 'POST', data: { code: '123456' } }
    });
    expect(callAt(1)).toMatchObject({
      url: '/api/v1/users/me/payment-password-changes',
      options: {
        method: 'POST',
        data: { verificationToken: 'verify-once-1', newPassword: '246810' }
      }
    });
  });
});
