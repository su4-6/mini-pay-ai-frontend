import { beforeEach, describe, expect, it, vi } from 'vitest';

const { httpRequestMock } = vi.hoisted(() => ({ httpRequestMock: vi.fn() }));

vi.mock('./http', () => ({
  httpRequest: httpRequestMock,
  buildQuery: (): string => '?type=RECHARGE&cursor=1&limit=20'
}));

import { bindBankCard, queryBankBalance, submitFunding } from './funding';

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

describe('银行卡与资金操作契约', () => {
  it('绑定银行卡只提交必要字段，不在前端伪造状态', async () => {
    httpRequestMock.mockResolvedValueOnce({
      cardId: 'card-1',
      bankName: '沙箱银行',
      maskedCardNo: '**** 8888',
      status: 'ACTIVE'
    });

    const card = await bindBankCard({
      holderName: '测试用户',
      cardNumber: '6222000000008888',
      verificationCode: '123456'
    });

    expect(card).toMatchObject({ cardId: 'card-1', status: 'ACTIVE' });
    expect(callAt(0)).toMatchObject({
      url: '/api/v1/bank-cards',
      options: {
        method: 'POST',
        data: {
          holderName: '测试用户',
          cardNumber: '6222000000008888',
          verificationCode: '123456'
        }
      }
    });
  });

  it('余额查询只把支付密码交给同源 BFF，并携带幂等键', async () => {
    httpRequestMock.mockResolvedValueOnce({
      availableAmountCent: 12_345,
      currency: 'CNY',
      asOf: '2026-09-29T13:43:14Z'
    });

    const balance = await queryBankBalance('card/1', '135790');

    expect(balance.availableFen).toBe(12_345);
    expect(balance.updatedAt).toBe('2026-09-29T13:43:14Z');
    const request = callAt(0);
    expect(request.url).toBe('/api/v1/bank-cards/card%2F1/balance-queries');
    expect(request.options.data).toEqual({ paymentPassword: '135790' });
    expect(request.options.headers?.['Idempotency-Key']).toEqual(expect.any(String));
    expect(request.options.data).not.toHaveProperty('authorizationToken');
  });

  it.each([
    ['RECHARGE', '/api/v1/recharge-orders', 'rechargeId'],
    ['WITHDRAWAL', '/api/v1/withdrawal-orders', 'withdrawalId']
  ] as const)('%s 走 BFF 完整确认链而不是直连支付服务', async (type, path, idField) => {
    httpRequestMock.mockResolvedValueOnce({
      [idField]: `${type.toLowerCase()}-1`,
      bankCardId: 'card-1',
      amountFen: 2_000,
      status: 'SUCCEEDED'
    });

    const order = await submitFunding({
      type,
      bankCardId: 'card-1',
      amountFen: 2_000,
      paymentPassword: '135790'
    });

    expect(order).toMatchObject({ type, amountFen: 2_000, status: 'SUCCEEDED' });
    const request = callAt(0);
    expect(request.url).toBe(path);
    expect(request.options.data).toEqual({
      bankCardId: 'card-1',
      amountFen: 2_000,
      paymentPassword: '135790'
    });
    expect(request.options.headers?.['Idempotency-Key']).toEqual(expect.any(String));
    expect(request.options.data).not.toHaveProperty('authorizationToken');
  });
});
