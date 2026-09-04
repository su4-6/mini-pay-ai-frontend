import { describe, expect, it } from 'vitest';
import { ApiProblemError, REQUEST_ID_HEADER, requestJson, toApiProblemError } from './index';
import { afterEach, vi } from 'vitest';

describe('api client primitives', () => {
  afterEach(() => vi.restoreAllMocks());
  it('preserves the stable request identifier from an RFC 9457 problem', () => {
    const error = toApiProblemError({
      type: 'https://docs.minipay.local/problems/invalid-request',
      title: 'Invalid request',
      status: 400,
      code: 'INVALID_REQUEST',
      requestId: 'req_123',
      detail: 'Safe detail'
    });

    expect(REQUEST_ID_HEADER).toBe('X-Request-Id');
    expect(error.problem.requestId).toBe('req_123');
    expect(error.message).toBe('Safe detail');
  });

  it('maps RFC 9457 responses to ApiProblemError', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      type: 'https://docs.minipay.local/problems/conflict',
      title: 'Conflict', status: 409, code: 'MERCHANT_VERSION_CONFLICT', requestId: 'req-2'
    }), { status: 409, headers: { 'Content-Type': 'application/problem+json' } }));

    await expect(requestJson('/api/v1/ops/merchants/m-1')).rejects.toMatchObject({
      name: 'ApiProblemError',
      problem: { code: 'MERCHANT_VERSION_CONFLICT', requestId: 'req-2' }
    } satisfies Partial<ApiProblemError>);
  });

  it('maps non-JSON upstream failures to a safe problem response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('<html>gateway error</html>', {
      status: 502,
      statusText: 'Bad Gateway',
      headers: { 'X-Request-Id': 'req-3' }
    }));

    await expect(requestJson('/api/v1/ops/dashboard')).rejects.toMatchObject({
      name: 'ApiProblemError',
      problem: {
        code: 'UNEXPECTED_RESPONSE',
        requestId: 'req-3',
        status: 502
      }
    } satisfies Partial<ApiProblemError>);
  });
});
