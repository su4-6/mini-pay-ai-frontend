import { toApiProblemError } from '@minipay/api-client';
import type { CollectionResolution, PaymentConfirmation, PreparedPayment } from '../types/consumer';
import { asRecord, readFen, readString } from './parsers';
import { httpRequest } from './http';
import { normalizeTransferStatus } from '../utils/transfer-status';

/**
 * 扫商户收款码付款（沙箱），与转账同构的三步，绝不合并：
 *   POST /api/v1/payments/scan                        {deepLink | merchantToken}
 *   POST /api/v1/payments/prepare                     {resolutionId, amountFen}
 *   POST /api/v1/payments/{paymentOrderId}/confirm    {amountFen, paymentPassword}
 *
 * 由 consumer-bff → payment-service / identity-service 完成，顺序与约束：
 *   1. scan 解析收款码：商户码返回一次性 `resolutionId`，个人码没有（不能走付款）；
 *   2. prepare 创建商户支付单并记住本会话准备的金额；
 *   3. confirm 先用支付密码换「绑定 paymentOrderId + 金额」的一次性授权令牌，再确认支付。
 *
 * 因此 confirm 的 `amountFen` 只能取 prepare 返回的权威值，绝不能用输入框里的本地值，
 * 也不得做四舍五入或浮点换算（授权令牌与金额绑定，服务端会拒绝不一致的金额）。
 * 支付密码只在本次调用栈中存在，不写缓存、不写日志、不进 URL。
 */

/**
 * 只有商户收款码能走付款流程：上游用它区分「商户码」（带一次性 resolutionId）与「个人码」。
 * 支付渠道与账单摘要是由 consumer-bff 固定的服务端常量（余额支付 /「扫码付款」），H5 不参与拼装。
 */
const MERCHANT_COLLECTION_TYPE = 'MERCHANT_COLLECTION';

function localProblem(code: string, title: string) {
  return toApiProblemError({
    type: 'about:blank',
    title,
    status: 0,
    code,
    requestId: 'local'
  });
}

function parseResolution(raw: unknown): CollectionResolution {
  const record = asRecord(raw);
  const resolution: CollectionResolution = {
    type: readString(record, 'type') ?? ''
  };
  const resolutionId = readString(record, 'resolutionId');
  if (resolutionId) resolution.resolutionId = resolutionId;
  const merchantId = readString(record, 'merchantId');
  if (merchantId) resolution.merchantId = merchantId;
  const merchantName = readString(record, 'merchantName');
  if (merchantName) resolution.merchantName = merchantName;
  const expiresAt = readString(record, 'expiresAt');
  if (expiresAt) resolution.expiresAt = expiresAt;
  const receiverDisplay = readString(record, 'receiverDisplay', 'receiverNickname');
  if (receiverDisplay) resolution.receiverDisplay = receiverDisplay;
  const receiverUserId = readString(record, 'receiverUserId');
  if (receiverUserId) resolution.receiverUserId = receiverUserId;
  return resolution;
}

/** 深链（minipay://…）与裸令牌走的是同一个上游字段二选一，这里按前缀判断。 */
function scanPayload(code: string): Record<string, string> {
  return /^[a-z][a-z0-9+.-]*:\/\//i.test(code) ? { deepLink: code } : { merchantToken: code };
}

export async function scanCollectionCode(code: string): Promise<CollectionResolution> {
  const trimmed = code.trim();
  if (!trimmed) {
    throw localProblem('COLLECTION_CODE_REQUIRED', '请填写或粘贴收款码内容');
  }
  const raw = await httpRequest<unknown>('/api/v1/payments/scan', {
    method: 'POST',
    data: scanPayload(trimmed)
  });
  return parseResolution(raw);
}

export function isMerchantResolution(resolution: CollectionResolution | null): boolean {
  return Boolean(resolution && resolution.type === MERCHANT_COLLECTION_TYPE && resolution.resolutionId);
}

export interface PreparePaymentInput {
  resolution: CollectionResolution;
  amountFen: number;
}

export async function prepareMerchantPayment(input: PreparePaymentInput): Promise<PreparedPayment> {
  const resolutionId = input.resolution.resolutionId;
  if (!resolutionId) {
    throw localProblem('PAYMENT_RESOLUTION_REQUIRED', '这不是商户收款码，无法发起付款');
  }
  if (!Number.isInteger(input.amountFen) || input.amountFen <= 0) {
    throw localProblem('PAYMENT_AMOUNT_REQUIRED', '付款金额缺失或无效');
  }
  const raw = await httpRequest<unknown>('/api/v1/payments/prepare', {
    method: 'POST',
    data: { resolutionId, amountFen: input.amountFen }
  });
  const record = asRecord(raw);
  const paymentOrderId = readString(record, 'paymentOrderId');
  if (!paymentOrderId) throw new Error('付款准备响应缺少 paymentOrderId');
  return {
    paymentOrderId,
    paymentOrderNo: readString(record, 'paymentOrderNo'),
    // 以服务端返回的金额为准：它才是与一次性授权令牌绑定的那个值。
    amountFen: readFen(record, 'amountFen', 'amountCent') ?? input.amountFen,
    expiresAt: readString(record, 'expiresAt') ?? '',
    merchantName: input.resolution.merchantName ?? input.resolution.receiverDisplay ?? '商户'
  };
}

/**
 * 支付授权令牌与 paymentOrderId + 金额绑定，因此这里同样做本地硬校验：
 * 非正整数一律拒绝，绝不把 0 或小数发给服务端（否则会为一笔真实付款签发 0 分授权）。
 */
function assertAuthorizedAmount(amountFen: number): void {
  if (!Number.isInteger(amountFen) || amountFen <= 0) {
    throw localProblem('PAYMENT_AMOUNT_REQUIRED', '付款金额缺失或无效');
  }
}

/**
 * @param amountFen 必须来自 `prepareMerchantPayment` 返回的 `PreparedPayment.amountFen`，
 *   即服务端确认过的支付单金额；禁止传入输入框的本地值。
 */
export async function confirmMerchantPayment(
  paymentOrderId: string,
  paymentPassword: string,
  amountFen: number
): Promise<PaymentConfirmation> {
  assertAuthorizedAmount(amountFen);
  const raw = await httpRequest<unknown>(
    `/api/v1/payments/${encodeURIComponent(paymentOrderId)}/confirm`,
    { method: 'POST', data: { amountFen, paymentPassword } }
  );
  const record = asRecord(raw);
  const paymentOrderNo = readString(record, 'paymentOrderNo', 'paymentOrderId');
  if (!paymentOrderNo) throw new Error('付款确认响应缺少 paymentOrderNo');
  const confirmation: PaymentConfirmation = {
    paymentOrderNo,
    status: normalizeTransferStatus(readString(record, 'status'))
  };
  const failureCode = readString(record, 'failureCode');
  if (failureCode) confirmation.failureCode = failureCode;
  return confirmation;
}
