import { createRequestId } from '@minipay/shared';
export type Role =
  | 'system_super_admin'
  | 'system_account_admin'
  | 'system_auditor'
  | 'platform_admin'
  | 'merchant_owner';
export interface Session {
  authenticated: boolean;
  loginUrl: string;
  admin?: { userId: string; displayName: string; roles: Role[] };
}
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}
export interface Account {
  userId: string;
  minipayNo: string;
  displayName: string;
  maskedMobile?: string;
  maskedEmail?: string;
  status: string;
  credentialType: string;
  onboardingStatus: string;
  loginPasswordSet: boolean;
  paymentPasswordSet: boolean;
  roles: Role[];
  version: number;
  createdAt: string;
}
export interface Wallet {
  accountId: string;
  accountNo: string;
  ownerType: string;
  ownerId: string;
  currency: string;
  accountRole: string;
  availableAmountCent: number;
  frozenAmountCent: number;
  status: string;
  updatedAt: string;
}
export interface Audit {
  auditId: string;
  actorUserId?: string;
  action: string;
  targetType: string;
  targetId: string;
  result: string;
  reason?: string;
  requestId: string;
  occurredAt: string;
}
export interface Merchant {
  merchantId: string;
  merchantNo: string;
  name: string;
  shortName?: string;
  status: string;
  ownerUserId: string;
  applicationCount: number;
  createdAt: string;
  updatedAt: string;
}
export interface PaymentOrder {
  paymentOrderNo: string;
  merchantId: string;
  merchantNo: string;
  merchantName: string;
  appId: string;
  merchantOrderNo: string;
  amountCent: number;
  currency: string;
  channel: string;
  subject: string;
  status: string;
  payerMasked?: string;
  createdAt: string;
}
export interface FoodOrder {
  orderRefId: string;
  userId: string;
  provider: string;
  externalOrderNo: string;
  paymentOrderId?: string | null;
  amountCent: number;
  currency: string;
  paymentStatus: string;
  fulfillmentStatus: string;
  refundStatus: string;
  expiresAt: string;
  createdAt: string;
  updatedAt: string;
}
export interface CollectionRecord {
  billId: string;
  ownerId: string;
  businessType: string;
  businessNo: string;
  source: string;
  direction: string;
  amountCent: number;
  counterpartyDisplay?: string | null;
  remark?: string | null;
  status: string;
  balanceAfterCent?: number | null;
  failureCode?: string | null;
  occurredAt: string;
  updatedAt: string;
}

export interface RefundOrder {
  refundNo: string;
  paymentOrderNo: string;
  merchantId: string;
  merchantNo: string;
  merchantName: string;
  amountCent: number;
  status: string;
  createdAt: string;
}
export interface TransferOrder {
  transferNo: string;
  payerMasked?: string;
  receiverMasked?: string;
  amountCent: number;
  status: string;
  createdAt: string;
}
export interface RechargeOrder {
  rechargeNo: string; userMasked?: string; amountCent: number; channel: string;
  bankName?: string; bankCardMasked?: string; status: string; createdAt: string;
}
export interface WithdrawalOrder {
  withdrawalNo: string; userMasked?: string; amountCent: number; bankName?: string;
  bankCardMasked?: string; status: string; createdAt: string;
}
export interface AdminBankCard {
  cardId: string; ownerUserId: string; provider: string; bankName: string; cardType: string;
  maskedCardNo: string; holderName: string; status: string; verifiedAt: string;
  createdAt: string; updatedAt: string;
}
export interface LedgerTransaction {
  transactionId: string;
  transactionNo: string;
  businessType: string;
  businessNo: string;
  debitTotalAmountCent: number;
  creditTotalAmountCent: number;
  occurredAt: string;
}
export interface LoginAudit {
  auditId: string;
  occurredAt: string;
  authenticationMethod: string;
  result: string;
  displayName?: string;
  requestId: string;
}
export interface ServiceHealth {
  code: string;
  name: string;
  status: 'UP' | 'DOWN';
  latencyMs: number;
}
export interface SystemHealth {
  status: 'UP' | 'DEGRADED';
  services: ServiceHealth[];
}
async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase();
  let csrf: Record<string, string> = {};
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = (await fetch('/api/v1/csrf', { credentials: 'include' }).then((r) =>
      r.json()
    )) as { headerName: string; token: string };
    csrf = { [token.headerName]: token.token };
  }
  const response = await fetch(url, {
    ...init,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'X-Request-Id': createRequestId(),
      ...csrf,
      ...init.headers
    }
  });
  const body = await response.json().catch(() => undefined) as
    | { code?: string; detail?: string }
    | undefined;
  if (!response.ok) {
    const code = body?.code;
    const messages: Record<string, string> = {
      MOBILE_ALREADY_BOUND: '该手机号已绑定其他账号',
      ACCOUNT_NOT_FOUND: '账号不存在或已被删除',
      ACCOUNT_VERSION_CONFLICT: '账号数据已更新，请刷新页面后重试',
      SELF_ADMIN_MUTATION_FORBIDDEN: '不能对当前登录账号执行此操作',
      LAST_SUPER_ADMIN_PROTECTED: '不能停用或调整最后一个超级管理员',
      SYSTEM_ADMIN_PROTECTED: '只有超级管理员可以操作系统管理员账号',
      SUPER_ADMIN_REQUIRED: '该操作仅限超级管理员执行',
      ADMIN_ROLE_REQUIRED: '当前账号没有管理端访问权限',
      ADMIN_WRITE_FORBIDDEN: '当前账号只有只读权限，不能执行变更',
      INVALID_BACKOFFICE_ROLE: '请选择有效的后台账号角色',
      INVALID_REASON: '操作原因应为 3 至 200 个字符',
      INVALID_REASON_ENCODING: '操作原因格式不正确，请重新输入'
    };
    throw new Error((code && messages[code]) ?? body?.detail ?? `请求失败（${response.status}）`);
  }
  return body as T;
}
const params = (input: Record<string, unknown>) => {
  const p = new URLSearchParams();
  Object.entries(input).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, String(v));
  });
  return p.toString();
};
export const adminApi = {
  session: () => request<Session>('/api/v1/session'),
  summary: () =>
    request<{
      consumers: number;
      merchantOwners: number;
      operators: number;
      administrators: number;
    }>('/api/v1/admin/summary'),
  accounts: (q: Record<string, unknown>) =>
    request<Page<Account>>(`/api/v1/admin/accounts?${params(q)}`),
  account: (userId: string) => request<Account>(`/api/v1/admin/accounts/${userId}`),
  createAccount: (body: { mobile: string; displayName: string; role: string }) =>
    mutate<Account>('/api/v1/admin/accounts/backoffice', 'POST', body, '创建后台账号'),
  accountAction: (account: Account, action: string, body: unknown, reason: string) =>
    mutate<Account>(`/api/v1/admin/accounts/${account.userId}/${action}`, 'POST', body, reason, {
      'If-Match': String(account.version)
    }),
  bankCards: (userId: string) =>
    request<AdminBankCard[]>(`/api/v1/admin/bank-cards?userId=${encodeURIComponent(userId)}`),
  status: (a: Account, status: string, reason: string) =>
    mutate<Account>(
      `/api/v1/admin/accounts/${a.userId}/status`,
      'PUT',
      { status, version: a.version },
      reason,
      { 'If-Match': String(a.version) }
    ),
  role: (a: Account, role: string, reason: string) =>
    mutate<Account>(
      `/api/v1/admin/accounts/${a.userId}/role`,
      'PUT',
      { role, version: a.version },
      reason,
      { 'If-Match': String(a.version) }
    ),
  ownPassword: (newPassword: string) =>
    mutate<{ configured: boolean; changedAt: string }>(
      '/api/v1/admin/me/password',
      'PUT',
      { newPassword },
      '管理员本人设置登录密码'
    ),
  merchants: (q: Record<string, unknown>) =>
    request<Page<Merchant>>(`/api/v1/admin/merchants?${params(q)}`),
  merchant: (merchantId: string) => request<Merchant>(`/api/v1/admin/merchants/${merchantId}`),
  orders: <T = PaymentOrder>(kind: string, q: Record<string, unknown>) =>
    request<Page<T>>(`/api/v1/admin/orders/${kind}?${params(q)}`),
  orderDetail: <T = Record<string, unknown>>(kind: string, orderNo: string) =>
    request<T>(`/api/v1/admin/orders/${kind}/${encodeURIComponent(orderNo)}`),
  wallets: (q: Record<string, unknown>) =>
    request<Page<Wallet>>(`/api/v1/admin/wallets?${params(q)}`),
  wallet: (accountId: string) => request<Wallet>(`/api/v1/admin/wallets/${accountId}`),
  walletBills: (accountId: string, q: Record<string, unknown>) =>
    request<Page<Record<string, unknown>>>(`/api/v1/admin/wallets/${accountId}/bills?${params(q)}`),
  ledger: (q: Record<string, unknown>) =>
    request<Page<LedgerTransaction>>(`/api/v1/admin/ledger-transactions?${params(q)}`),
  ledgerDetail: (transactionId: string) =>
    request<Record<string, unknown>>(`/api/v1/admin/ledger-transactions/${transactionId}`),
  audits: (q: Record<string, unknown>) =>
    request<Page<Audit>>(`/api/v1/admin/action-audits?${params(q)}`),
  loginAudits: (q: Record<string, unknown>) =>
    request<Page<LoginAudit>>(`/api/v1/admin/login-audits?${params(q)}`),
  systemHealth: () => request<SystemHealth>('/api/v1/admin/system-health')
};
async function mutate<T>(
  url: string,
  method: string,
  body: unknown,
  reason: string,
  extraHeaders: Record<string, string> = {}
) {
  return request<T>(url, {
    method,
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': createRequestId(),
      // Fetch request headers are ByteString-only. Encode UTF-8 audit reasons
      // as an ASCII URI component and decode them at the Identity boundary.
      'X-Reason': encodeURIComponent(reason.trim()),
      ...extraHeaders
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
}
