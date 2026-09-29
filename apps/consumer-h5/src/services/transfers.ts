import { toApiProblemError } from '@minipay/api-client';
import type { PreparedTransfer, TransferConfirmation, TransferDetail, TransferPage } from '../types/consumer';
import { asArray, asRecord, readFen, readPage, readString } from './parsers';
import { buildQuery, httpRequest } from './http';
import { normalizeTransferStatus } from '../utils/transfer-status';

/**
 * 转账（prepare → confirm → 查询恢复）：
 *   POST   /api/v1/transfers/prepare            {payeeIdentifier, amountFen, remark?}
 *   POST   /api/v1/transfers/{intentId}/confirm {amountFen, paymentPassword}
 *   DELETE /api/v1/transfers/{intentId}
 *   GET    /api/v1/transfers?cursor=&limit=
 *   GET    /api/v1/transfers/{transferNo}
 *
 * confirm 必须带上 `amountFen`：consumer-bff 把它透传给身份服务签发
 * **限定金额**的一次性支付授权令牌（绑定 intentId + 金额，防篡改）。
 * 因此该金额只能取 prepare 返回的意图值，绝不能用用户在输入框里可改的值，
 * 也不得做四舍五入或浮点换算。
 *
 * 金额只用整数「分」；支付密码只在本次调用栈中存在，不写缓存、不写日志、不进 URL。
 */

export interface PrepareTransferInput {
  payeeIdentifier: string;
  amountFen: number;
  remark?: string;
}

function parseIntent(raw: unknown, input: PrepareTransferInput): PreparedTransfer {
  const record = asRecord(raw);
  const transferIntentId = readString(record, 'transferIntentId', 'intentId');
  if (!transferIntentId) throw new Error('转账准备响应缺少 transferIntentId');
  return {
    transferIntentId,
    payeeMasked: readString(record, 'payeeMasked', 'payeeDisplay') ?? input.payeeIdentifier,
    amountFen: readFen(record, 'amountFen', 'amountCent') ?? input.amountFen,
    expiresAt: readString(record, 'expiresAt') ?? '',
    remark: input.remark,
    payeeIdentifier: input.payeeIdentifier
  };
}

function parseTransferDetail(raw: unknown): TransferDetail {
  const record = asRecord(raw);
  const receiverUserId = readString(record, 'receiverUserId');
  return {
    transferNo: readString(record, 'transferNo', 'transferId') ?? '',
    status: normalizeTransferStatus(readString(record, 'status')),
    amountFen: readFen(record, 'amountFen', 'amountCent') ?? 0,
    payeeMasked:
      readString(record, 'payeeMasked', 'receiverMasked') ??
      (receiverUserId ? `用户 ${receiverUserId.slice(0, 8)}…` : undefined),
    payerMasked: readString(record, 'payerMasked'),
    remark: readString(record, 'remark'),
    failureCode: readString(record, 'failureCode'),
    createdAt: readString(record, 'createdAt', 'updatedAt'),
    updatedAt: readString(record, 'updatedAt')
  };
}

export async function prepareTransfer(input: PrepareTransferInput): Promise<PreparedTransfer> {
  const raw = await httpRequest<unknown>('/api/v1/transfers/prepare', {
    method: 'POST',
    data: {
      payeeIdentifier: input.payeeIdentifier,
      amountFen: input.amountFen,
      ...(input.remark ? { remark: input.remark } : {})
    }
  });
  return parseIntent(raw, input);
}

export interface PrepareCollectionTransferInput {
  deepLink: string;
  amountFen: number;
  remark?: string;
}

/** Personal QR codes use the normal transfer ledger path after server-side code revalidation. */
export async function prepareTransferFromCollectionCode(
  input: PrepareCollectionTransferInput
): Promise<PreparedTransfer> {
  const raw = await httpRequest<unknown>('/api/v1/transfers/prepare-from-collection-code', {
    method: 'POST',
    data: {
      deepLink: input.deepLink,
      amountFen: input.amountFen,
      ...(input.remark ? { remark: input.remark } : {})
    }
  });
  const record = asRecord(raw);
  const transferIntentId = readString(record, 'transferIntentId', 'intentId');
  if (!transferIntentId) throw new Error('扫码转账准备响应缺少 transferIntentId');
  return {
    transferIntentId,
    payeeMasked: readString(record, 'payeeMasked', 'payeeDisplay') ?? '个人用户',
    amountFen: readFen(record, 'amountFen', 'amountCent') ?? input.amountFen,
    expiresAt: readString(record, 'expiresAt') ?? '',
    remark: input.remark,
    payeeIdentifier: 'PERSONAL_COLLECTION_CODE'
  };
}

/**
 * 支付授权令牌与 intent 金额绑定，因此 confirm 的金额必须是 prepare 返回的权威值。
 * 这里做本地硬校验：非正整数一律拒绝，绝不允许把 0 或小数发给服务端
 * （否则会为一笔真实转账签发 0 分授权）。
 */
function assertAuthorizedAmount(amountFen: number): void {
  if (!Number.isInteger(amountFen) || amountFen <= 0) {
    throw toApiProblemError({
      type: 'about:blank',
      title: '转账金额缺失或无效',
      status: 0,
      code: 'TRANSFER_AMOUNT_REQUIRED',
      requestId: 'local'
    });
  }
}

/**
 * @param amountFen 必须来自 `prepareTransfer` 返回的 `PreparedTransfer.amountFen`，
 *   即服务端确认过的转账意图金额；禁止传入输入框的本地值。
 */
export async function confirmTransfer(
  transferIntentId: string,
  paymentPassword: string,
  amountFen: number
): Promise<TransferConfirmation> {
  assertAuthorizedAmount(amountFen);
  const raw = await httpRequest<unknown>(
    `/api/v1/transfers/${encodeURIComponent(transferIntentId)}/confirm`,
    { method: 'POST', data: { amountFen, paymentPassword } }
  );
  const record = asRecord(raw);
  const transferNo = readString(record, 'transferNo');
  if (!transferNo) throw new Error('转账确认响应缺少 transferNo');
  return { transferNo, status: normalizeTransferStatus(readString(record, 'status')) };
}

export async function cancelTransfer(transferIntentId: string): Promise<void> {
  await httpRequest<unknown>(`/api/v1/transfers/${encodeURIComponent(transferIntentId)}`, {
    method: 'DELETE'
  });
}

export async function fetchTransferPage(cursor: string | null, limit = 20): Promise<TransferPage> {
  const raw = await httpRequest<unknown>(
    `/api/v1/transfers${buildQuery({ cursor: cursor ?? undefined, limit })}`,
    { method: 'GET' }
  );
  // Payment 的全量转账查询直接返回数组；Wallet 的按交易对手查询仍返回分页信封。
  const page = Array.isArray(raw) ? { items: asArray(raw) } : readPage(raw);
  return { items: page.items.map(parseTransferDetail), nextCursor: page.nextCursor };
}

export async function fetchTransferDetail(transferNo: string): Promise<TransferDetail> {
  const raw = await httpRequest<unknown>(`/api/v1/transfers/${encodeURIComponent(transferNo)}`, {
    method: 'GET'
  });
  return parseTransferDetail(raw);
}
