import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * 扫码付款的请求体是资金授权路径的关键契约，与转账同构：
 *   scan    → 只带 deepLink 或 merchantToken 之一；
 *   prepare → 只带 resolutionId + amountFen（渠道与账单摘要由服务端固定，前端不参与）；
 *   confirm → 只带 prepare 返回的权威金额 + 支付密码。
 * 金额少传/传 0 会为一笔真实付款签发 0 分授权，因此这几条必须锁死。
 */
const { httpRequestMock } = vi.hoisted(() => ({ httpRequestMock: vi.fn() }));

vi.mock('./http', () => ({
  httpRequest: httpRequestMock,
  buildQuery: (): string => ''
}));

import {
  confirmMerchantPayment,
  isMerchantResolution,
  prepareMerchantPayment,
  scanCollectionCode
} from './payments';

interface RecordedCall {
  url: string;
  options: { method?: string; data?: Record<string, unknown> };
}

function callAt(index: number): RecordedCall {
  const call = httpRequestMock.mock.calls[index];
  if (!call) throw new Error(`缺少第 ${index} 次请求记录`);
  return { url: String(call[0]), options: (call[1] ?? {}) as RecordedCall['options'] };
}

const MERCHANT_CODE = 'minipay://collect/merchant?token=abc123';

beforeEach(() => {
  httpRequestMock.mockReset();
});

describe('扫码付款：识别收款码', () => {
  it('深链走 deepLink 字段，且只发这一个字段', async () => {
    httpRequestMock.mockResolvedValueOnce({
      type: 'MERCHANT_COLLECTION',
      resolutionId: 'res-1',
      merchantName: 'Demo Merchant',
      allowedChannels: ['WALLET_BALANCE']
    });
    const resolution = await scanCollectionCode(MERCHANT_CODE);

    expect(resolution).toMatchObject({ type: 'MERCHANT_COLLECTION', resolutionId: 'res-1' });
    const scan = callAt(0);
    expect(scan.url).toBe('/api/v1/payments/scan');
    expect(scan.options.method).toBe('POST');
    expect(scan.options.data).toEqual({ deepLink: MERCHANT_CODE });
  });

  it('裸令牌走 merchantToken 字段', async () => {
    httpRequestMock.mockResolvedValueOnce({ type: 'MERCHANT_COLLECTION', resolutionId: 'res-2' });
    await scanCollectionCode('  bare-token-1  ');

    expect(callAt(0).options.data).toEqual({ merchantToken: 'bare-token-1' });
  });

  it('个人收款码没有 resolutionId，不能走付款流程', async () => {
    httpRequestMock.mockResolvedValueOnce({
      type: 'PERSONAL_COLLECTION',
      receiverUserId: 'user-1',
      receiverDisplay: '138****0000'
    });
    const resolution = await scanCollectionCode('minipay://collect/personal?token=xyz');

    expect(isMerchantResolution(resolution)).toBe(false);
    await expect(
      prepareMerchantPayment({ resolution, amountFen: 100 })
    ).rejects.toThrow(/商户收款码/);
    // 被本地拦下：只有 scan 发出过请求。
    expect(httpRequestMock).toHaveBeenCalledTimes(1);
  });

  it('空收款码直接拒绝，不发出请求', async () => {
    await expect(scanCollectionCode('   ')).rejects.toThrow();
    expect(httpRequestMock).not.toHaveBeenCalled();
  });
});

describe('扫码付款：创建支付单', () => {
  it('只带 resolutionId 与 amountFen，渠道与摘要由服务端固定', async () => {
    httpRequestMock.mockResolvedValueOnce({
      paymentOrderId: 'order-1',
      paymentOrderNo: 'PAY20260101000001',
      amountCent: 1_800,
      expiresAt: '2026-01-01T00:05:00Z'
    });
    const order = await prepareMerchantPayment({
      resolution: { type: 'MERCHANT_COLLECTION', resolutionId: 'res-1', merchantName: 'Demo Merchant' },
      amountFen: 1_500
    });

    // 服务端返回的金额优先于请求金额。
    expect(order.amountFen).toBe(1_800);
    const prepare = callAt(0);
    expect(prepare.url).toBe('/api/v1/payments/prepare');
    expect(Object.keys(prepare.options.data ?? {}).sort()).toEqual(['amountFen', 'resolutionId']);
    ['paymentMethod', 'subject', 'merchantName', 'channel'].forEach((key) => {
      expect(prepare.options.data).not.toHaveProperty(key);
    });
  });

  it('缺少 paymentOrderId 的准备响应被视为异常', async () => {
    httpRequestMock.mockResolvedValueOnce({ amountCent: 100 });
    await expect(
      prepareMerchantPayment({
        resolution: { type: 'MERCHANT_COLLECTION', resolutionId: 'res-1' },
        amountFen: 100
      })
    ).rejects.toThrow(/paymentOrderId/);
  });
});

describe('扫码付款：确认付款的授权金额来源', () => {
  it('请求体带支付单权威金额与支付密码，且只有这两个字段', async () => {
    httpRequestMock.mockResolvedValueOnce({
      paymentOrderId: 'order-2',
      paymentOrderNo: 'PAY20260101000002',
      amountCent: 2_500,
      expiresAt: '2026-01-01T00:05:00Z'
    });
    const order = await prepareMerchantPayment({
      resolution: { type: 'MERCHANT_COLLECTION', resolutionId: 'res-3' },
      amountFen: 2_500
    });

    httpRequestMock.mockResolvedValueOnce({
      paymentOrderNo: 'PAY20260101000002',
      status: 'SUCCEEDED'
    });
    const confirmation = await confirmMerchantPayment(order.paymentOrderId, '135790', order.amountFen);

    // 状态经 normalizeTransferStatus 归一化：上游 SUCCEEDED → 本地 SUCCESS。
    expect(confirmation).toEqual({ paymentOrderNo: 'PAY20260101000002', status: 'SUCCESS' });

    const confirm = callAt(1);
    expect(confirm.url).toBe('/api/v1/payments/order-2/confirm');
    expect(confirm.options.method).toBe('POST');
    expect(confirm.options.data).toEqual({ amountFen: 2_500, paymentPassword: '135790' });
    expect(Object.keys(confirm.options.data ?? {}).sort()).toEqual(['amountFen', 'paymentPassword']);
  });

  it('金额缺失、为 0、小数或负数一律拒绝且不发出请求', async () => {
    await expect(confirmMerchantPayment('order-3', '135790', 0)).rejects.toThrow();
    await expect(confirmMerchantPayment('order-3', '135790', 12.5)).rejects.toThrow();
    await expect(confirmMerchantPayment('order-3', '135790', -1)).rejects.toThrow();
    await expect(confirmMerchantPayment('order-3', '135790', Number.NaN)).rejects.toThrow();
    expect(httpRequestMock).not.toHaveBeenCalled();
  });

  it('缺少支付单号的确认响应被视为异常', async () => {
    httpRequestMock.mockResolvedValueOnce({ status: 'SUCCEEDED' });
    await expect(confirmMerchantPayment('order-4', '135790', 100)).rejects.toThrow(/paymentOrderNo/);
  });
});
