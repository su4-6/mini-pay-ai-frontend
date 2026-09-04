import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiProblemError } from '@minipay/api-client';
import { requestJson } from './auth';

afterEach(() => {
  vi.restoreAllMocks();
});

describe('OPS API security client', () => {
  it('loads a server CSRF token and attaches it to unsafe requests', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'server-token'
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ accepted: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      }));

    await requestJson<{ accepted: boolean }>('/api/v1/example', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ value: 1 })
    });

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/ops-csrf');
    expect(fetchMock.mock.calls[1]?.[1]).toMatchObject({
      credentials: 'include'
    });
    expect(fetchMock.mock.calls[1]?.[1]?.headers).toMatchObject({
      'X-CSRF-TOKEN': 'server-token',
      'Content-Type': 'application/json'
    });
  });

  it('preserves RFC 9457 fields as ApiProblemError', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(JSON.stringify({
      type: 'https://docs.minipay.local/problems/access-denied',
      title: 'Forbidden',
      status: 403,
      code: 'ACCESS_DENIED',
      requestId: 'request-123',
      instance: '/api/v1/login-audits'
    }), {
      status: 403,
      headers: { 'Content-Type': 'application/problem+json' }
    }));

    const result = requestJson('/api/v1/login-audits');

    await expect(result).rejects.toBeInstanceOf(ApiProblemError);
    await expect(result).rejects.toMatchObject({
      problem: {
        code: 'ACCESS_DENIED',
        requestId: 'request-123',
        instance: '/api/v1/login-audits'
      }
    });
  });
});
