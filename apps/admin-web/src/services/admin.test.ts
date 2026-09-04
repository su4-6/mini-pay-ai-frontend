import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminApi, type Account } from './admin';

const account: Account = {
  userId: '0198a651-5b55-7000-8000-000000000001', minipayNo: 'M10001', displayName: '测试账号',
  status: 'ACTIVE', credentialType: 'PASSWORD', onboardingStatus: 'COMPLETED',
  loginPasswordSet: true, paymentPasswordSet: true, roles: ['merchant_owner'], version: 7,
  createdAt: '2026-08-10T00:00:00Z'
};

describe('admin API contracts', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('keeps identity audit paging one-based and sends real result values', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({items:[],page:2,size:20,total:0}), {
      status: 200, headers: {'Content-Type':'application/json'}
    }));
    vi.stubGlobal('fetch', fetchMock);
    await adminApi.audits({page:2,size:20,result:'FAILED'});
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/admin/action-audits?page=2&size=20&result=FAILED');
  });

  it('sends version, If-Match, reason and an idempotency key for account status changes', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({headerName:'X-CSRF-TOKEN',token:'csrf'}), {
        status: 200, headers: {'Content-Type':'application/json'}
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({...account,status:'DISABLED',version:8}), {
        status: 200, headers: {'Content-Type':'application/json'}
      }));
    vi.stubGlobal('fetch', fetchMock);
    await adminApi.status(account, 'DISABLED', '风险账号停用');
    const request = fetchMock.mock.calls[1]?.[1] as RequestInit;
    const headers = request.headers as Record<string,string>;
    expect(headers['If-Match']).toBe('7');
    expect(headers['X-Reason']).toBe(encodeURIComponent('风险账号停用'));
    expect(headers['Idempotency-Key']).toBeTruthy();
    expect(JSON.parse(String(request.body))).toMatchObject({status:'DISABLED',version:7});
  });

  it.each(['unlock','sessions/revoke','credential-reset','payment-password-reset'])(
    'keeps optimistic locking and audit headers for %s',
    async (action) => {
      const fetchMock = vi.fn()
        .mockResolvedValueOnce(new Response(JSON.stringify({headerName:'X-CSRF-TOKEN',token:'csrf'}), {
          status: 200, headers: {'Content-Type':'application/json'}
        }))
        .mockResolvedValueOnce(new Response(JSON.stringify(account), {
          status: 200, headers: {'Content-Type':'application/json'}
        }));
      vi.stubGlobal('fetch', fetchMock);

      await adminApi.accountAction(account, action, undefined, '安全审计操作');

      expect(fetchMock.mock.calls[1]?.[0]).toBe(`/api/v1/admin/accounts/${account.userId}/${action}`);
      const request = fetchMock.mock.calls[1]?.[1] as RequestInit;
      const headers = request.headers as Record<string,string>;
      expect(headers['If-Match']).toBe(String(account.version));
      expect(headers['X-Reason']).toBe(encodeURIComponent('安全审计操作'));
      expect(headers['Idempotency-Key']).toBeTruthy();
    }
  );

  it('encodes Chinese reasons into browser-safe ASCII headers when creating accounts', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({headerName:'X-CSRF-TOKEN',token:'csrf'}), {
        status: 200, headers: {'Content-Type':'application/json'}
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify(account), {
        status: 201, headers: {'Content-Type':'application/json'}
      }));
    vi.stubGlobal('fetch', fetchMock);

    await adminApi.createAccount({mobile:'13900000009',displayName:'只读',role:'system_auditor'});

    const headers = (fetchMock.mock.calls[1]?.[1] as RequestInit).headers as Record<string,string>;
    expect(headers['X-Reason']).toBe(encodeURIComponent('创建后台账号'));
    expect(headers['X-Reason']).toMatch(/^[\x20-\x7E]+$/);
  });

  it('updates the credential used by administrator password login', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({headerName:'X-CSRF-TOKEN',token:'csrf'}), {
        status: 200, headers: {'Content-Type':'application/json'}
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({configured:true,changedAt:'2026-08-10T00:00:00Z'}), {
        status: 200, headers: {'Content-Type':'application/json'}
      }));
    vi.stubGlobal('fetch', fetchMock);

    await adminApi.ownPassword('AdminSecure123');

    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/admin/me/password');
    expect(JSON.parse(String((fetchMock.mock.calls[1]?.[1] as RequestInit).body)))
      .toEqual({newPassword:'AdminSecure123'});
  });
});
