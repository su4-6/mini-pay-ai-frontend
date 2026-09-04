import { afterEach, describe, expect, it, vi } from 'vitest';
import { loginWithPassword } from './auth';

describe('administrator password login', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('submits the administrator password and captcha to Identity', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }), {
        status: 200, headers: { 'Content-Type': 'application/json' }
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ redirectUrl: 'http://localhost:8002/' }), {
        status: 200, headers: { 'Content-Type': 'application/json' }
      }));
    vi.stubGlobal('fetch', fetchMock);

    await loginWithPassword({
      phone: '18800009999', password: 'AdminSecure123', captchaId: 'captcha-1', captchaCode: 'ABCD'
    });

    expect(fetchMock.mock.calls[1]?.[0]).toBe('/identity/login/password');
    const request = fetchMock.mock.calls[1]?.[1] as RequestInit;
    expect(String(request.body)).toContain('phone=18800009999');
    expect(String(request.body)).toContain('password=AdminSecure123');
    expect(String(request.body)).toContain('captchaId=captcha-1');
  });
});
