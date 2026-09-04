/**
 * Placeholders for types generated from the backend OpenAPI documents.
 * Business DTOs must be generated after the public contracts are published.
 */
export type RequestId = string;

export interface ProblemDetails {
  type: string;
  title: string;
  status: number;
  code: string;
  requestId: RequestId;
  detail?: string;
  instance?: string;
}

export interface AuthenticatedAdmin {
  userId: string;
  displayName: string;
  roles: string[];
  permissions: string[];
}

export interface SessionResponse {
  authenticated: boolean;
  loginUrl: string;
  admin: AuthenticatedAdmin | null;
}

export interface CsrfResponse {
  headerName: string;
  parameterName: string;
  token: string;
}

export interface CaptchaChallengeResponse {
  captchaId: string;
  imageUrl: string;
  expiresAt: string;
}

/** 登录成功响应：跳转地址（保存的 OAuth2 授权请求或运营平台首页）。 */
export interface LoginAttemptResponse {
  redirectUrl: string;
}

/** 短信验证码下发响应。 */
export interface SmsChallengeResponse {
  challengeId: string;
  maskedPhone: string;
  expiresAt: string;
  resendAfterSeconds: number;
  /** 仅演示/控制台环境可返回明文验证码。 */
  demoCode?: string;
}

export interface LoginAuditItem {
  auditId: string;
  occurredAt: string;
  authenticationMethod: 'PASSWORD' | 'SMS';
  result: string;
  displayName?: string;
  requestId: string;
}

export interface LoginAuditPage {
  items: LoginAuditItem[];
  page: number;
  size: number;
  total: number;
}

export type MerchantStatus = 'ACTIVE' | 'DISABLED' | 'FROZEN';

export type MerchantType = 'PERSONAL' | 'INDIVIDUAL' | 'ENTERPRISE';

export interface OpsMerchant {
  merchantId: string;
  merchantNo: string;
  name: string;
  shortName: string;
  contactName?: string | null;
  contactMobile?: string | null;
  contactEmail?: string | null;
  remark?: string | null;
  merchantType: MerchantType;
  mccCode?: string | null;
  address?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  shopImages?: string | null;
  profileComplete: boolean;
  status: MerchantStatus;
  freezeReason?: string | null;
  accountLinked: boolean;
  ownerUserId?: string | null;
  applicationCount: number;
  deletable: boolean;
  deletionBlockedReason?: 'MERCHANT_HAS_APPLICATIONS' | 'MERCHANT_HAS_TRANSACTIONS' | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ImageUploadRequest {
  fileName: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
}

export interface ImageUploadGrant {
  uploadUrl: string;
  objectKey: string;
  requiredHeaders: Record<string, string>;
  expiresAt: string;
}

export interface ImageReadUrlsResponse {
  urls: Record<string, string>;
}

export interface OpsMerchantPage {
  items: OpsMerchant[];
  page: number;
  size: number;
  total: number;
}

export type MerchantApplyStatus =
  | 'DRAFT'
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'SUPPLEMENT';

export interface OpsMerchantApply {
  id: number;
  userId: string;
  merchantType: MerchantType;
  shopName: string;
  mccCode?: string | null;
  address?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  shopImages?: string | null;
  contactName: string;
  contactMobile: string;
  contactEmail?: string | null;
  remark?: string | null;
  applyStatus: MerchantApplyStatus;
  rejectReason?: string | null;
  auditAdminId?: string | null;
  resultantMerchantId?: string | null;
  applyTime: string;
  auditTime?: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface OpsMerchantApplyPage {
  items: OpsMerchantApply[];
  page: number;
  size: number;
  total: number;
}

export type ApplicationStatus = 'ACTIVE' | 'DISABLED';

export type ApplicationDeletionBlockedReason =
  | 'APPLICATION_MUST_BE_DISABLED'
  | 'APPLICATION_HAS_TRANSACTIONS'
  | 'APPLICATION_HAS_DEPENDENCIES';

export interface OpsApplication {
  applicationId: string;
  appId: string;
  name: string;
  merchantId: string;
  merchantNo: string;
  merchantName: string;
  merchantStatus: MerchantStatus;
  status: ApplicationStatus;
  hasTransactions: boolean;
  deletable: boolean;
  deletionBlockedReason?: ApplicationDeletionBlockedReason | null;
  recentTransactionCount?: number;
  lastTransactionAt?: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface OpsApplicationPage {
  items: OpsApplication[];
  page: number;
  size: number;
  total: number;
}

export interface OpsApplicationSummary {
  totalCount: number;
  activeCount: number;
  disabledCount: number;
  unavailableCount: number;
}

export type ApplicationApplyStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'SUPPLEMENT';

export interface OpsApplicationApply {
  id: number;
  userId: string;
  merchantId: string;
  merchantNo?: string | null;
  merchantName?: string | null;
  name: string;
  applyStatus: ApplicationApplyStatus;
  rejectReason?: string | null;
  auditAdminId?: string | null;
  resultantApplicationId?: string | null;
  applyTime: string;
  auditTime?: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface OpsApplicationApplyPage {
  items: OpsApplicationApply[];
  page: number;
  size: number;
  total: number;
}

export type OpsDashboardRange = '7d' | '30d';

export interface OpsDashboardSummary {
  paymentAmountCent: number;
  paymentCount: number;
  successRateBasisPoints: number;
  refundAmountCent: number;
  activeMerchantCount: number;
}

export interface OpsDailyMetric {
  date: string;
  submittedPaymentCount: number;
  successfulPaymentCount: number;
  paymentAmountCent: number;
  successfulRefundCount: number;
  refundAmountCent: number;
}

export interface OpsPendingCounts {
  abnormalPaymentCount: number;
  abnormalRefundCount: number;
  abnormalTransferCount: number;
  failedNotificationCount: number;
}

export interface OpsDashboard {
  range: OpsDashboardRange;
  timezone: string;
  from: string;
  to: string;
  dataAsOf: string;
  summary: OpsDashboardSummary;
  trend: OpsDailyMetric[];
  pending: OpsPendingCounts;
}

// ── 运营端订单查询（OPS-05/06/07/08）──
export type OpsPaymentOrderStatus = 'PROCESSING' | 'SUCCEEDED' | 'FAILED';
export type OpsRefundOrderStatus = 'PROCESSING' | 'SUCCEEDED' | 'FAILED';
export type OpsTransferOrderStatus = 'PROCESSING' | 'SUCCEEDED' | 'FAILED';

export interface OpsPaymentOrder {
  paymentOrderNo: string;
  merchantId?: string | null;
  merchantNo?: string | null;
  merchantName?: string | null;
  appId: string;
  merchantOrderNo?: string | null;
  amountCent: number;
  currency: string;
  channel?: string | null;
  subject: string;
  status: string;
  payerMasked?: string | null;
  createdAt: string;
}

export interface OpsPaymentOrderDetail extends OpsPaymentOrder {
  failureCode?: string | null;
  updatedAt: string;
}

export interface OpsPaymentOrderPage {
  items: OpsPaymentOrder[];
  page: number;
  size: number;
  total: number;
}

export interface OpsRefund {
  refundNo: string;
  paymentOrderNo: string;
  merchantId?: string | null;
  merchantNo?: string | null;
  merchantName?: string | null;
  amountCent: number;
  status: string;
  createdAt: string;
}

export interface OpsRefundDetail extends OpsRefund {
  reason?: string | null;
  updatedAt: string;
}

export interface OpsRefundPage {
  items: OpsRefund[];
  page: number;
  size: number;
  total: number;
}

export interface OpsTransfer {
  transferNo: string;
  payerMasked?: string | null;
  receiverMasked?: string | null;
  amountCent: number;
  status: string;
  createdAt: string;
}

export interface OpsTransferDetail extends OpsTransfer {
  failureCode?: string | null;
  updatedAt: string;
}

export interface OpsTransferPage {
  items: OpsTransfer[];
  page: number;
  size: number;
  total: number;
}

export interface OpsRecharge {
  rechargeNo: string;
  userMasked?: string | null;
  amountCent: number;
  channel: string;
  bankName?: string | null;
  bankCardMasked?: string | null;
  status: string;
  createdAt: string;
}

export interface OpsRechargeDetail extends OpsRecharge {
  failureCode?: string | null;
  updatedAt: string;
}

export interface OpsRechargePage {
  items: OpsRecharge[];
  page: number;
  size: number;
  total: number;
}

export interface OpsWithdrawal {
  withdrawalNo: string;
  userMasked?: string | null;
  amountCent: number;
  bankName: string;
  bankCardMasked: string;
  status: string;
  createdAt: string;
}

export interface OpsWithdrawalDetail extends OpsWithdrawal {
  bankRequestNo?: string | null;
  failureCode?: string | null;
  updatedAt: string;
}

export interface OpsWithdrawalPage {
  items: OpsWithdrawal[];
  page: number;
  size: number;
  total: number;
}

export interface OpsFoodOrder {
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

export interface OpsFoodOrderPage {
  items: OpsFoodOrder[];
  page: number;
  size: number;
  total: number;
}

export interface OpsCollectionRecord {
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

export interface OpsCollectionRecordPage {
  items: OpsCollectionRecord[];
  page: number;
  size: number;
  total: number;
  summary: {
    collectionCount: number;
    collectionAmountCent: number;
    refundCount: number;
    refundAmountCent: number;
    netAmountCent: number;
  };
}

export type OpsNotificationType = 'PAYMENT' | 'REFUND';
export type OpsNotificationStatus = 'PENDING' | 'RETRYING' | 'SUCCEEDED' | 'FAILED';

export interface OpsNotification {
  notificationId: string;
  eventId: string;
  type: string;
  status: string;
  attempts: number;
  nextAttemptAt?: string | null;
  responseSummary?: string | null;
  createdAt: string;
}

export interface OpsNotificationAttempt {
  attemptNo: number;
  automated: boolean;
  httpStatus?: number | null;
  result: string;
  requestSummary?: string | null;
  responseSummary?: string | null;
  occurredAt: string;
}

export interface OpsNotificationDetail extends OpsNotification {
  requestSummary?: string | null;
  history: OpsNotificationAttempt[];
}
