import type { BankCard, CollectionCode, WalletBill, WalletBillPage, WalletSummary } from '../types/consumer';
import { asArray, asRecord, readFen, readPage, readString } from './parsers';
import { buildQuery, httpRequest } from './http';

/**
 * 钱包、账单、收款码、银行卡：
 *   GET /api/v1/wallet
 *   GET /api/v1/wallet/bills?cursor=&limit=
 *   GET /api/v1/collection-code
 *   GET /api/v1/bank-cards                     （P1）
 */

function parseWallet(raw: unknown): WalletSummary {
  const record = asRecord(raw);
  const availableFen = readFen(record, 'availableFen', 'availableAmountFen', 'availableAmountCent', 'balanceFen') ?? 0;
  const frozenFen = readFen(record, 'frozenFen', 'frozenAmountFen', 'frozenAmountCent') ?? 0;
  const totalFen = readFen(record, 'totalFen', 'totalAmountFen', 'totalAmountCent') ?? availableFen + frozenFen;
  return {
    walletId: readString(record, 'walletId', 'accountId'),
    status: readString(record, 'status'),
    currency: readString(record, 'currency') ?? 'CNY',
    availableFen,
    frozenFen,
    totalFen,
    updatedAt: readString(record, 'updatedAt'),
    sandboxNotice: readString(record, 'sandboxNotice')
  };
}

function parseBill(raw: unknown): WalletBill {
  const record = asRecord(raw);
  return {
    billId: readString(record, 'billId', 'id') ?? readString(record, 'businessNo') ?? '',
    businessType: readString(record, 'businessType') ?? 'UNKNOWN',
    businessNo: readString(record, 'businessNo') ?? '',
    direction: readString(record, 'direction') ?? 'DEBIT',
    amountFen: readFen(record, 'amountFen', 'amountCent') ?? 0,
    counterpartyDisplay: readString(record, 'counterpartyDisplay', 'counterparty'),
    remark: readString(record, 'remark'),
    status: readString(record, 'status') ?? 'UNKNOWN',
    balanceAfterFen: readFen(record, 'balanceAfterFen', 'balanceAfterCent'),
    failureCode: readString(record, 'failureCode'),
    occurredAt: readString(record, 'occurredAt', 'createdAt') ?? ''
  };
}

function parseCollectionCode(raw: unknown): CollectionCode {
  const record = asRecord(raw);
  const code =
    readString(record, 'code', 'payload', 'deepLink', 'content', 'token', 'collectionCode') ?? '';
  return {
    code,
    qrImageUrl: readString(record, 'qrImageUrl', 'imageUrl', 'qrCodeUrl'),
    expiresAt: readString(record, 'expiresAt'),
    sandboxNotice: readString(record, 'sandboxNotice')
  };
}

function parseBankCard(raw: unknown): BankCard {
  const record = asRecord(raw);
  return {
    cardId: readString(record, 'cardId', 'id') ?? '',
    bankName: readString(record, 'bankName') ?? '银行',
    maskedCardNo: readString(record, 'maskedCardNo', 'cardNoMasked') ?? '',
    status: readString(record, 'status') ?? 'ACTIVE',
    cardType: readString(record, 'cardType')
  };
}

export async function fetchWallet(): Promise<WalletSummary> {
  return parseWallet(await httpRequest<unknown>('/api/v1/wallet', { method: 'GET' }));
}

export async function fetchBillPage(cursor: string | null, limit = 20): Promise<WalletBillPage> {
  const raw = await httpRequest<unknown>(
    `/api/v1/wallet/bills${buildQuery({ cursor: cursor ?? undefined, limit })}`,
    { method: 'GET' }
  );
  const page = readPage(raw);
  return { items: page.items.map(parseBill), nextCursor: page.nextCursor };
}

export async function fetchCollectionCode(): Promise<CollectionCode> {
  return parseCollectionCode(await httpRequest<unknown>('/api/v1/collection-code', { method: 'GET' }));
}

export async function fetchBankCards(): Promise<BankCard[]> {
  const raw = await httpRequest<unknown>('/api/v1/bank-cards', { method: 'GET' });
  // Payment 的银行卡列表契约直接返回 JSON 数组；同时兼容未来的分页信封。
  // 不能一律交给 readPage：数组会被视为空对象，造成“绑定成功但列表仍为空”。
  const items = Array.isArray(raw) ? asArray(raw) : readPage(raw).items;
  return items.map(parseBankCard).filter((card) => card.cardId);
}
