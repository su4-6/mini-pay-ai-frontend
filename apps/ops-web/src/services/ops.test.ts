import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  createApplication,
  createMerchant,
  deleteApplication,
  deleteMerchant,
  getApplications,
  getMerchants
} from './ops';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  });
}

describe('operations API client', () => {
  afterEach(() => vi.restoreAllMocks());

  it('uses zero-based pagination and only includes active filters', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      items: [], page: 0, size: 20, total: 0
    }));

    await getMerchants({
      page: 0, size: 20, merchantNo: ' M2026 ', name: ' 星河 ', status: 'ACTIVE'
    });

    expect(fetch.mock.calls[0][0]).toBe(
      '/api/v1/ops/merchants?page=0&size=20&merchantNo=M2026&name=%E6%98%9F%E6%B2%B3&status=ACTIVE'
    );
  });

  it('adds CSRF and preserves the supplied idempotency key for writes', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'csrf-token'
      }))
      .mockResolvedValueOnce(jsonResponse({
        merchantId: 'm-1', merchantNo: 'M1', name: '星河便利店', status: 'ACTIVE',
        applicationCount: 0, deletable: true, createdAt: '2026-08-03T00:00:00Z',
        updatedAt: '2026-08-03T00:00:00Z', version: 0
      }, 201));

    await createMerchant({
      name: '星河便利店', shortName: '星河便利', contactName: '张三',
      contactMobile: '13800000001', merchantType: 'PERSONAL', status: 'ACTIVE'
    }, 'idem-merchant-create-0001');

    const options = fetch.mock.calls[1][1] as RequestInit;
    expect(options.method).toBe('POST');
    expect(options.headers).toMatchObject({
      'X-CSRF-TOKEN': 'csrf-token',
      'Idempotency-Key': 'idem-merchant-create-0001'
    });
    expect(JSON.parse(options.body as string)).toMatchObject({
      name: '星河便利店', shortName: '星河便利', contactName: '张三',
      contactMobile: '13800000001', status: 'ACTIVE'
    });
  });

  it('uses If-Match and accepts an empty 204 delete response', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'csrf-token'
      }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));

    await deleteMerchant({ merchantId: 'm-1', version: 7 }, 'idem-delete-merchant-0001');

    expect((fetch.mock.calls[1][1] as RequestInit).headers).toMatchObject({ 'If-Match': '"7"' });
  });

  it('serializes independent application filters', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      items: [], page: 0, size: 20, total: 0
    }));

    await getApplications({
      page: 0, size: 20, appId: ' mp_app_01 ', name: ' 收银 ',
      merchantId: 'merchant-1', status: 'DISABLED'
    });

    expect(fetch.mock.calls[0][0]).toBe(
      '/api/v1/ops/applications?page=0&size=20&appId=mp_app_01&name=%E6%94%B6%E9%93%B6&merchantId=merchant-1&status=DISABLED'
    );
  });

  it('uses idempotency and optimistic locking for application writes', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'csrf-token'
      }))
      .mockResolvedValueOnce(jsonResponse({ applicationId: 'a-1' }, 201))
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'csrf-token'
      }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));

    await createApplication({
      merchantId: 'merchant-1', name: '星河收银台', status: 'ACTIVE'
    }, 'idem-create-application-0001');
    await deleteApplication({
      applicationId: 'a-1', version: 4
    }, 'idem-delete-application-0001');

    expect((fetch.mock.calls[1][1] as RequestInit).headers).toMatchObject({
      'Idempotency-Key': 'idem-create-application-0001'
    });
    expect((fetch.mock.calls[3][1] as RequestInit).headers).toMatchObject({
      'Idempotency-Key': 'idem-delete-application-0001', 'If-Match': '"4"'
    });
  });
});
