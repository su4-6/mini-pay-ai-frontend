import { createRequestId } from '@minipay/shared';
import type { BankBalance, BankCard, FundingOrder, FundingOrderPage } from '../types/consumer';
import { asRecord, readFen, readPage, readString } from './parsers';
import { buildQuery, httpRequest } from './http';

function headers(): Record<string, string> {
  return { 'Idempotency-Key': createRequestId() };
}

function parseCard(raw: unknown): BankCard {
  const record = asRecord(raw);
  return {
    cardId: readString(record, 'cardId') ?? '',
    bankName: readString(record, 'bankName') ?? '银行',
    maskedCardNo: readString(record, 'maskedCardNo') ?? '',
    status: readString(record, 'status') ?? 'ACTIVE',
    cardType: readString(record, 'cardType')
  };
}

export async function bindBankCard(input: {
  holderName: string;
  cardNumber: string;
  verificationCode: string;
}): Promise<BankCard> {
  return parseCard(await httpRequest('/api/v1/bank-cards', { method: 'POST', data: input }));
}

export async function unbindBankCard(cardId: string): Promise<void> {
  await httpRequest(`/api/v1/bank-cards/${encodeURIComponent(cardId)}`, { method: 'DELETE' });
}

export async function queryBankBalance(cardId: string, paymentPassword: string): Promise<BankBalance> {
  const record = asRecord(await httpRequest<unknown>(
    `/api/v1/bank-cards/${encodeURIComponent(cardId)}/balance-queries`,
    { method: 'POST', headers: headers(), data: { paymentPassword } }
  ));
  return {
    availableFen: readFen(record, 'availableFen', 'availableCent', 'balanceCent') ?? 0,
    currency: readString(record, 'currency') ?? 'CNY',
    updatedAt: readString(record, 'updatedAt')
  };
}

export async function submitFunding(input: {
  type: 'RECHARGE' | 'WITHDRAWAL';
  bankCardId: string;
  amountFen: number;
  paymentPassword: string;
}): Promise<FundingOrder> {
  const path = input.type === 'RECHARGE' ? '/api/v1/recharge-orders' : '/api/v1/withdrawal-orders';
  return parseFunding(await httpRequest(path, {
    method: 'POST',
    headers: headers(),
    data: {
      bankCardId: input.bankCardId,
      amountFen: input.amountFen,
      paymentPassword: input.paymentPassword
    }
  }), input.type);
}

function parseFunding(raw: unknown, type: 'RECHARGE' | 'WITHDRAWAL'): FundingOrder {
  const record = asRecord(raw);
  return {
    orderId: readString(record, type === 'RECHARGE' ? 'rechargeId' : 'withdrawalId') ?? '',
    orderNo: readString(record, type === 'RECHARGE' ? 'rechargeNo' : 'withdrawalNo'),
    type,
    bankCardId: readString(record, 'bankCardId') ?? '',
    amountFen: readFen(record, 'amountFen', 'amountCent') ?? 0,
    status: readString(record, 'status') ?? 'UNKNOWN',
    failureCode: readString(record, 'failureCode'),
    updatedAt: readString(record, 'updatedAt')
  };
}

export async function fetchFundingOrders(type: 'RECHARGE' | 'WITHDRAWAL'): Promise<FundingOrderPage> {
  const raw = await httpRequest<unknown>(`/api/v1/funding-orders${buildQuery({ type, cursor: 1, limit: 20 })}`, {
    method: 'GET'
  });
  const page = readPage(raw);
  return { items: page.items.map((item) => parseFunding(item, type)), nextCursor: page.nextCursor };
}
