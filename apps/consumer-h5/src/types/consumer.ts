/**
 * C 端（consumer-h5）业务契约。
 *
 * 这些类型刻意留在应用内，而不是放进 `@minipay/api-contracts`：
 * consumer-bff 的 BFF 契约（`/api/v1/session`、`/api/v1/wallet`、`/api/v1/transfers/prepare` …）
 * 属于 C 端应用私有契约，共享包只承载跨端稳定的公共模型。
 *
 * 金额字段一律为人民币「分」（整数）。字段命名以下发的 BFF 契约为准（`...Fen`），
 * 读取时通过 `services/parsers.ts` 容忍后端采用 `...Cent` 等同义命名。
 */

/** `GET /api/v1/session`（未登录分支） */
export interface ConsumerSessionAnonymous {
  authenticated: false;
}

/** `POST /api/v1/session` / `GET /api/v1/session`（已登录分支） */
export interface ConsumerSessionProfile {
  authenticated: true;
  userId: string;
  phone: string;
  displayName: string;
  payPasswordSet: boolean;
  onboardingRequired: boolean;
  realNameStatus: string;
  realNameVerified: boolean;
}

export type ConsumerSession = ConsumerSessionAnonymous | ConsumerSessionProfile;

export function isAuthenticatedSession(session: ConsumerSession | undefined): session is ConsumerSessionProfile {
  return session?.authenticated === true;
}

/** `POST /api/v1/session/sms` → 202 */
export interface SmsChallenge {
  challengeId: string;
  expiresAt: string;
  /** 仅演示/沙箱环境返回明文验证码。 */
  demoCode?: string;
}

/** `GET /api/v1/wallet` */
export interface WalletSummary {
  walletId?: string;
  status?: string;
  currency: string;
  availableFen: number;
  frozenFen: number;
  totalFen: number;
  updatedAt?: string;
  sandboxNotice?: string;
}

/** `GET /api/v1/wallet/bills?cursor=&limit=` */
export interface WalletBill {
  billId: string;
  businessType: string;
  businessNo: string;
  direction: string;
  amountFen: number;
  counterpartyDisplay?: string;
  remark?: string;
  status: string;
  balanceAfterFen?: number;
  failureCode?: string;
  occurredAt: string;
}

export interface WalletBillPage {
  items: WalletBill[];
  nextCursor?: string;
}

/** `GET /api/v1/collection-code` */
export interface CollectionCode {
  code: string;
  /** 若后端直接返回可渲染的二维码图片（data URL 或 https URL）则优先展示。 */
  qrImageUrl?: string;
  expiresAt?: string;
  sandboxNotice?: string;
}

/** `GET /api/v1/bank-cards`（P1） */
export interface BankCard {
  cardId: string;
  bankName: string;
  maskedCardNo: string;
  status: string;
  cardType?: string;
}

/** Consumer Identity profile, never contains a full mobile or identity number. */
export interface ConsumerProfileDetail {
  userId: string;
  nickname: string;
  miniPayNo: string;
  avatarUrl?: string;
  version: number;
  legalNameMasked?: string;
}

export interface ConsumerCapabilities {
  onboardingCompleted: boolean;
  realNameStatus: string;
  realNameVerified: boolean;
  payPasswordSet: boolean;
}

export interface AccountSecurityOverview {
  maskedMobile: string;
  maskedEmail?: string;
  paymentPasswordSet: boolean;
}

export interface VerificationChallenge {
  challengeId: string;
  maskedTarget?: string;
  expiresAt?: string;
  demoCode?: string;
}

export interface RealNameVerification {
  verificationId: string;
  status: string;
  legalNameMasked?: string;
  idNumberMasked?: string;
  failureCode?: string;
}

export interface BankBalance {
  availableFen: number;
  currency: string;
  updatedAt?: string;
}

export interface FundingOrder {
  orderId: string;
  orderNo?: string;
  type: 'RECHARGE' | 'WITHDRAWAL';
  bankCardId: string;
  amountFen: number;
  status: string;
  failureCode?: string;
  updatedAt?: string;
}

export interface FundingOrderPage {
  items: FundingOrder[];
  nextCursor?: string;
}

/** `POST /api/v1/transfers/prepare` → 转账意图 */
export interface TransferIntent {
  transferIntentId: string;
  payeeMasked: string;
  amountFen: number;
  expiresAt: string;
}

/** 转账意图的本地快照：只在内存 Zustand 中保存，不写入任何持久化存储。 */
export interface PreparedTransfer extends TransferIntent {
  remark?: string;
  payeeIdentifier: string;
}

export type TransferStatus =
  | 'PROCESSING'
  | 'SUCCESS'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CLOSED'
  | 'CANCELLED'
  | 'EXPIRED';

/** `POST /api/v1/transfers/{intentId}/confirm` */
export interface TransferConfirmation {
  transferNo: string;
  status: TransferStatus;
}

/** `GET /api/v1/transfers/{transferNo}` */
export interface TransferDetail {
  transferNo: string;
  status: TransferStatus;
  amountFen: number;
  payeeMasked?: string;
  payerMasked?: string;
  remark?: string;
  failureCode?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** `GET /api/v1/transfers?cursor=&limit=` */
export interface TransferPage {
  items: TransferDetail[];
  nextCursor?: string;
}

/**
 * `POST /api/v1/payments/scan`（扫商户收款码付款的第一步）。
 *
 * 两种收款码返回的结构不同，字段全部可选：
 *   - 商户码 `type=MERCHANT_COLLECTION`：带 `resolutionId`（一次性，第二步创建支付单要用）；
 *   - 个人码：只有收款人展示信息，没有 `resolutionId`，不能走付款流程（应改用转账）。
 * 金额与状态一律以后端为准，这里只承载展示与流转所需的字段。
 */
export interface CollectionResolution {
  type: string;
  resolutionId?: string;
  merchantId?: string;
  merchantName?: string;
  allowedChannels?: string[];
  expiresAt?: string;
  receiverUserId?: string;
  receiverDisplay?: string;
  receiverNickname?: string;
}

/** `POST /api/v1/payments/prepare` → 商户支付单（金额为后端权威值） */
export interface PreparedPayment {
  paymentOrderId: string;
  paymentOrderNo?: string;
  amountFen: number;
  expiresAt: string;
  merchantName: string;
}

/** `POST /api/v1/payments/{paymentOrderId}/confirm` */
export interface PaymentConfirmation {
  paymentOrderNo: string;
  status: TransferStatus;
  failureCode?: string;
}

/** AI：会话 */
export interface AiConversation {
  id: string;
  title: string;
  status?: string;
  lastMessageAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AiConversationPage {
  items: AiConversation[];
  nextCursor?: string;
}

export type AiMessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM' | 'TOOL';

/** AI：消息 */
export interface AiMessage {
  id: string;
  runId?: string;
  role: AiMessageRole;
  content: string;
  cardType?: string;
  sequenceNo?: number;
  createdAt: string;
}

export interface AiMessagePage {
  items: AiMessage[];
  nextCursor?: string;
}

/** `POST /api/v1/ai/conversations/{id}/messages` → `{runId}` */
export interface AiRunHandle {
  runId: string;
}

/** SSE 事件信封（`GET /api/v1/ai/runs/{runId}/events`） */
export interface AiRunEvent {
  id: string;
  type: string;
  conversationId?: string;
  runId?: string;
  occurredAt?: string;
  traceId?: string;
  payload: Record<string, unknown>;
}

export type AiStreamPhase = 'IDLE' | 'STREAMING' | 'RECONNECTING' | 'FAILED' | 'COMPLETED';
