import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * confirm 的请求体是资金授权路径的关键契约：
 * consumer-bff 会把 `amountFen` 透传给身份服务签发「限定金额」的一次性支付授权令牌。
 * 少传/传 0 会为一笔真实转账签发 0 分授权，因此这里必须断言：
 *   1. 请求体带 `amountFen`；
 *   2. 该值等于 prepare 返回的意图金额（服务端权威值），
 *      而不是用户可编辑的输入值（二者不一致时以服务端为准）；
 *   3. 不含任何其它金额来源字段。
 */
const { httpRequestMock } = vi.hoisted(() => ({ httpRequestMock: vi.fn() }));

vi.mock('./http', () => ({
  httpRequest: httpRequestMock,
  buildQuery: (params: Record<string, string | number | boolean | undefined>): string => {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value === undefined || value === '') return;
      search.set(key, String(value));
    });
    const query = search.toString();
    return query ? `?${query}` : '';
  }
}));

import { confirmTransfer, fetchTransferPage, prepareTransfer, prepareTransferFromCollectionCode } from './transfers';

interface RecordedCall {
  url: string;
  options: { method?: string; data?: Record<string, unknown> };
}

function callAt(index: number): RecordedCall {
  const call = httpRequestMock.mock.calls[index];
  if (!call) throw new Error(`缺少第 ${index} 次请求记录`);
  return { url: String(call[0]), options: (call[1] ?? {}) as RecordedCall['options'] };
}

beforeEach(() => {
  httpRequestMock.mockReset();
});

describe('转账 confirm 的授权金额来源', () => {
  it('个人收款码走专用准备接口，浏览器不提交 receiverUserId', async () => {
    httpRequestMock.mockResolvedValueOnce({
      transferIntentId: 'intent-qr',
      payeeMasked: '小满（张*）',
      amountFen: 100,
      expiresAt: '2026-01-01T00:05:00Z'
    });
    const intent = await prepareTransferFromCollectionCode({
      deepLink: 'minipay://collect/personal?token=abc',
      amountFen: 100,
      remark: '扫码转账'
    });

    expect(intent.payeeMasked).toBe('小满（张*）');
    const request = callAt(0);
    expect(request.url).toBe('/api/v1/transfers/prepare-from-collection-code');
    expect(request.options.data).toEqual({
      deepLink: 'minipay://collect/personal?token=abc',
      amountFen: 100,
      remark: '扫码转账'
    });
    expect(request.options.data).not.toHaveProperty('receiverUserId');
  });

  it('请求体带 prepare 返回的 amountFen 与支付密码，且只有这两个字段', async () => {
    httpRequestMock.mockResolvedValueOnce({
      transferIntentId: 'intent-1',
      payeeMasked: '138****0000',
      amountFen: 12_345,
      expiresAt: '2026-01-01T00:00:05Z'
    });
    const intent = await prepareTransfer({ payeeIdentifier: '13800000000', amountFen: 12_345 });

    httpRequestMock.mockResolvedValueOnce({ transferNo: 'TR2026010100001', status: 'PROCESSING' });
    const result = await confirmTransfer(intent.transferIntentId, '135790', intent.amountFen);

    expect(result).toEqual({ transferNo: 'TR2026010100001', status: 'PROCESSING' });

    const confirm = callAt(1);
    expect(confirm.url).toBe('/api/v1/transfers/intent-1/confirm');
    expect(confirm.options.method).toBe('POST');
    expect(confirm.options.data).toEqual({ amountFen: 12_345, paymentPassword: '135790' });
    expect(Object.keys(confirm.options.data ?? {}).sort()).toEqual(['amountFen', 'paymentPassword']);
  });

  it('服务端返回的意图金额优先于请求金额（证明用的不是用户输入值）', async () => {
    // 用户填了 100.00 元，服务端确认后的意图金额是 123.45 元：授权必须按服务端金额签发。
    httpRequestMock.mockResolvedValueOnce({
      transferIntentId: 'intent-2',
      payeeMasked: '139****1111',
      amountFen: 12_345,
      expiresAt: '2026-01-01T00:00:05Z'
    });
    const intent = await prepareTransfer({ payeeIdentifier: '13900001111', amountFen: 10_000 });
    expect(intent.amountFen).toBe(12_345);

    httpRequestMock.mockResolvedValueOnce({ transferNo: 'TR2026010100002', status: 'SUCCESS' });
    await confirmTransfer(intent.transferIntentId, '135790', intent.amountFen);

    expect(callAt(1).options.data?.amountFen).toBe(12_345);
  });

  it('请求体不含任何其它金额来源字段', async () => {
    httpRequestMock.mockResolvedValueOnce({ transferNo: 'TR2026010100003', status: 'PROCESSING' });
    await confirmTransfer('intent-3', '135790', 999);

    const body = callAt(0).options.data ?? {};
    ['amount', 'amountCent', 'amountYuan', 'amountInput', 'payeeIdentifier', 'remark', 'transferIntentId'].forEach(
      (key) => {
        expect(body).not.toHaveProperty(key);
      }
    );
  });

  it('金额缺失或为 0 时直接拒绝，且不发出任何请求（不静默发 0）', async () => {
    await expect(confirmTransfer('intent-4', '135790', 0)).rejects.toThrow();
    expect(httpRequestMock).not.toHaveBeenCalled();
  });

  it('小数、负数、NaN 金额一律拒绝且不发出请求', async () => {
    await expect(confirmTransfer('intent-5', '135790', 12.5)).rejects.toThrow();
    await expect(confirmTransfer('intent-5', '135790', -1)).rejects.toThrow();
    await expect(confirmTransfer('intent-5', '135790', Number.NaN)).rejects.toThrow();
    expect(httpRequestMock).not.toHaveBeenCalled();
  });

  it('缺少 transferNo 的确认响应被视为异常', async () => {
    httpRequestMock.mockResolvedValueOnce({ status: 'PROCESSING' });
    await expect(confirmTransfer('intent-6', '135790', 100)).rejects.toThrow(/transferNo/);
  });
});

describe('转账记录列表契约', () => {
  it('读取 Payment 直接返回的数组并使用 transferId 进入详情', async () => {
    httpRequestMock.mockResolvedValueOnce([
      {
        transferId: '019d-transfer-1',
        receiverUserId: '019d-receiver-12345678',
        amountCent: 100,
        status: 'SUCCEEDED',
        updatedAt: '2026-09-28T15:00:00Z'
      }
    ]);

    await expect(fetchTransferPage(null, 20)).resolves.toEqual({
      items: [
        expect.objectContaining({
          transferNo: '019d-transfer-1',
          payeeMasked: '用户 019d-rec…',
          amountFen: 100,
          status: 'SUCCESS',
          createdAt: '2026-09-28T15:00:00Z'
        })
      ],
      nextCursor: undefined
    });
    expect(callAt(0).url).toBe('/api/v1/transfers?limit=20');
  });

  it('仍兼容 Wallet 的分页信封', async () => {
    httpRequestMock.mockResolvedValueOnce({
      items: [{ transferNo: 'TR-2', amountFen: 200, status: 'PROCESSING' }],
      page: 1,
      size: 20,
      total: 1
    });

    await expect(fetchTransferPage('1', 20)).resolves.toMatchObject({
      items: [expect.objectContaining({ transferNo: 'TR-2', amountFen: 200 })]
    });
  });
});
