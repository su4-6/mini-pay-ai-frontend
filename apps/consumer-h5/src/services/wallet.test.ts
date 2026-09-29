import { beforeEach, describe, expect, it, vi } from 'vitest';

const { httpRequestMock } = vi.hoisted(() => ({ httpRequestMock: vi.fn() }));

vi.mock('./http', () => ({
  httpRequest: httpRequestMock,
  buildQuery: vi.fn(() => '')
}));

import { fetchBankCards, fetchBillPage } from './wallet';

beforeEach(() => {
  httpRequestMock.mockReset();
});

describe('账单对手方展示', () => {
  it('优先展示身份服务返回的真实昵称', async () => {
    httpRequestMock.mockResolvedValueOnce({
      items: [{
        billId: 'bill-1', businessType: 'TRANSFER', businessNo: 'T1', direction: 'CREDIT',
        amountFen: 1000, counterpartyDisplay: '站内付款人', status: 'SUCCEEDED',
        occurredAt: '2026-09-29T09:00:00Z', counterpartyProfile: { nickname: '小米' }
      }]
    });

    await expect(fetchBillPage(null)).resolves.toEqual(expect.objectContaining({
      items: [expect.objectContaining({ counterpartyDisplay: '小米' })]
    }));
  });
});

describe('银行卡列表契约', () => {
  it('读取 Payment 直接返回的银行卡数组', async () => {
    httpRequestMock.mockResolvedValueOnce([
      {
        cardId: 'card-1',
        bankName: '沙箱银行',
        cardType: 'DEBIT',
        maskedCardNo: '**** **** **** 8888',
        status: 'ACTIVE'
      }
    ]);

    await expect(fetchBankCards()).resolves.toEqual([
      expect.objectContaining({ cardId: 'card-1', maskedCardNo: '**** **** **** 8888' })
    ]);
  });

  it('仍兼容分页信封并过滤缺少 cardId 的脏记录', async () => {
    httpRequestMock.mockResolvedValueOnce({
      items: [
        { cardId: 'card-2', bankName: '演示银行', maskedCardNo: '**** 6666' },
        { bankName: '无效记录' }
      ]
    });

    await expect(fetchBankCards()).resolves.toHaveLength(1);
  });
});
