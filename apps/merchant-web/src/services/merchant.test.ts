import { afterEach, describe, expect, it, vi } from 'vitest';
import { merchantApi } from './merchant';

describe('merchant authentication messages', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('shows a Chinese message when an SMS code is requested again within 60 seconds', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        headerName: 'X-CSRF-TOKEN', token: 'csrf-token'
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: 'SMS_RESEND_TOO_SOON', detail: 'too many requests'
      }), { status: 429, headers: { 'Content-Type': 'application/problem+json' } }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(merchantApi.sendLoginCode('13900000001', 'captcha-id', 'ABCD'))
      .rejects.toThrow('验证码已发送，请在 60 秒后再试');
  });
});
