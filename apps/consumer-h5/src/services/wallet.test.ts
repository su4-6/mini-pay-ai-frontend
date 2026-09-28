import { beforeEach, describe, expect, it, vi } from 'vitest';

const { httpRequestMock } = vi.hoisted(() => ({ httpRequestMock: vi.fn() }));

vi.mock('./http', () => ({
  httpRequest: httpRequestMock,
  buildQuery: vi.fn(() => '')
}));

import { fetchBankCards } from './wallet';

beforeEach(() => {
  httpRequestMock.mockReset();
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
