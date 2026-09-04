import { createRequestId } from '@minipay/shared';

export interface MerchantSession {
  authenticated: boolean;
  expiresAt: string | null;
  phone?: string | null;
  passwordConfigured: boolean;
}

export interface Merchant {
  merchantId: string;
  merchantNo: string;
  name: string;
  shortName: string;
  category: string;
  contactName: string | null;
  contactMobile: string | null;
  contactEmail?: string | null;
  address?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  shopImages?: string | null;
  status: 'ACTIVE' | 'DISABLED' | 'FROZEN';
  receiveLocked: boolean;
  remark?: string | null;
  initialized: boolean;
  profileConfirmationRequired: boolean;
  version: number;
}

export interface MerchantApply {
  id: number;
  merchantType: 'PERSONAL' | 'ENTERPRISE';
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
  applyStatus: 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUPPLEMENT';
  rejectReason?: string | null;
  resultantMerchantId?: string | null;
  version: number;
  updatedAt: string;
}

export interface Dashboard {
  days: number;
  paymentAmountCent: number;
  paymentCount: number;
  refundAmountCent: number;
  refundCount: number;
  todayPaymentAmountCent: number;
  todayPaymentCount: number;
  todayRefundAmountCent: number;
  todayRefundCount: number;
  cumulativePaymentAmountCent: number;
  cumulativePaymentCount: number;
  cumulativeRefundAmountCent: number;
  cumulativeRefundCount: number;
  dataAsOf: string | null;
  items: Array<{
    statDate: string;
    paymentAmountCent: number;
    paymentCount: number;
    refundAmountCent: number;
    refundCount: number;
  }>;
}

export interface MerchantApplication {
  applicationId: string;
  appId: string;
  appName: string;
  status: 'ACTIVE' | 'DISABLED';
  notifyUrl: string | null;
  refundNotifyUrl: string | null;
  ipWhiteList: string[];
  permissions: string[];
  availableChannels: string[];
  defaultApplication: boolean;
  version: number;
}

export interface MerchantOrder {
  paymentOrderNo: string;
  merchantOrderNo: string;
  appId: string;
  amountCent: number;
  currency: string;
  subject: string;
  channel: string;
  allowedChannels: string | null;
  status: string;
  expiresAt: string | null;
  createdAt: string;
  updatedAt: string;
  refundNo?: string | null;
  refundAmountCent?: number | null;
  refundStatus?: string | null;
  refundReason?: string | null;
}

export interface MerchantOrderPage {
  items: MerchantOrder[];
  page: number;
  size: number;
  total: number;
}

export interface WalletSummary {
  walletId: string;
  availableAmountCent: number;
  frozenAmountCent: number;
  totalAmountCent: number;
  currency: string;
  status: string;
  annualOutflowYear: number;
  annualOutflowLimitCent: number;
  annualOutflowUsedCent: number;
  annualOutflowRemainingCent: number;
}

export interface WalletBill {
  billId: string;
  businessType: string;
  businessNo: string;
  direction: string;
  amountCent: number;
  counterpartyDisplay?: string | null;
  remark?: string | null;
  status: string;
  balanceAfterCent?: number | null;
  occurredAt: string;
}

export interface CaptchaChallenge {
  captchaId: string;
  imageUrl: string;
  expiresAt: string;
}

const problemText: Record<string, string> = {
  SMS_RESEND_TOO_SOON: '验证码已发送，请在 60 秒后再试',
  AUTH_RATE_LIMITED: '操作过于频繁，请稍后再试',
  SMS_CODE_INVALID: '短信验证码错误，请重新输入',
  SMS_CODE_EXPIRED: '短信验证码已过期，请重新获取',
  SMS_CODE_LOCKED: '验证码尝试次数过多，请稍后再试',
  MOBILE_INVALID: '请输入正确的 11 位手机号',
  ACCOUNT_DISABLED: '账号当前不可用，请联系平台管理员',
  SMS_DELIVERY_UNAVAILABLE: '短信服务暂时不可用，请稍后再试',
  CAPTCHA_INVALID: '图形验证码错误，请重新输入',
  CAPTCHA_EXPIRED: '图形验证码已过期，请刷新后重试',
  CSRF_TOKEN_INVALID: '页面安全令牌已失效，请刷新页面',
  AUTHENTICATION_CHALLENGE_REJECTED: '账号、密码或验证码不正确',
  INVALID_MERCHANT_PASSWORD: '商户登录密码错误',
  CURRENT_MERCHANT_PASSWORD_INVALID: '当前密码不正确',
  CONTACT_MOBILE_LOCKED: '联系电话为登录账号手机号，不可修改',
  MERCHANT_PASSWORD_LOCKED: '密码连续错误次数过多，账号已锁定十分钟',
  MERCHANT_REAUTHENTICATION_REQUIRED: '登录状态已失效，请重新登录',
  MERCHANT_NOT_FOUND: '当前账号尚未入驻商户',
  MERCHANT_NOT_INITIALIZED: '当前商户尚未完成初始化',
  MERCHANT_WRITE_FORBIDDEN: '当前商户状态不允许执行该操作',
  MERCHANT_COLLECTION_UNAVAILABLE: '当前商户或应用暂不可收款',
  MERCHANT_APPLY_DUPLICATE_OPEN: '当前账号已有一个进行中的同名入驻申请，请到“申请记录”补充资料后重新提交',
  MERCHANT_APPLY_NOT_FOUND: '入驻申请不存在或已被处理',
  MERCHANT_APPLY_VERSION_CONFLICT: '入驻申请已更新，请刷新后重试',
  MERCHANT_APPLY_NOT_RESUBMITTABLE: '该入驻申请当前状态不允许重新提交',
  INVALID_MERCHANT_CATEGORY: '请选择正确的经营类目',
  INVALID_MERCHANT_NAME: '商户名称不符合要求（2-40 个字符）',
  INVALID_MERCHANT_SHORT_NAME: '商户简称不符合要求（2-32 个字符）',
  INVALID_SHOP_NAME: '经营名称不符合要求（2-64 个字符）',
  INVALID_CONTACT_NAME: '联系人姓名不符合要求',
  INVALID_CONTACT_MOBILE: '联系电话格式不正确',
  INVALID_CONTACT_EMAIL: '联系邮箱格式不正确',
  INVALID_LATITUDE: '纬度超出有效范围（-90 ~ 90）',
  INVALID_LONGITUDE: '经度超出有效范围（-180 ~ 180）',
  INVALID_FIELD_LENGTH: '字段长度超出限制',
  INVALID_PAGE: '分页参数不正确',
  INVALID_APPLICATION_NAME: '应用名称不符合要求（2-40 个字符）',
  MERCHANT_NOT_ACTIVE: '当前商户不可用，无法执行该操作',
  APPLICATION_APPLY_DUPLICATE_PENDING: '该商户已有一个进行中的应用申请，请等待审核结果',
  APPLICATION_APPLY_NAME_CONFLICT: '该商户下已存在同名应用，请换一个名称',
  APPLICATION_APPLY_NOT_FOUND: '应用申请不存在或已被处理',
  APPLICATION_APPLY_NOT_PENDING: '该应用申请已不在待审核状态',
  APPLICATION_APPLY_NOT_RESUBMITTABLE: '该应用申请当前状态不允许重新提交',
  APPLICATION_APPLY_VERSION_CONFLICT: '应用申请已更新，请刷新后重试',
  APPLICATION_DISABLED: '应用已停用',
  APPLICATION_NOT_FOUND: '应用不存在或已被删除',
  APPLICATION_SECRET_ALREADY_VIEWED: '应用密钥已领取，遗失后请重置密钥',
  APPLICATION_VERSION_CONFLICT: '应用配置已更新，请刷新后重试',
  COLLECTION_CODE_VERSION_CONFLICT: '收款码状态已变化，请刷新后重试',
  PAYMENT_NOT_REFUNDABLE: '当前订单不可退款',
  PAYMENT_ALREADY_REFUNDED: '该订单已经退款',
  MERCHANT_REFUND_INSUFFICIENT_BALANCE: '店主个人钱包余额不足，退款未执行',
  INSUFFICIENT_SCOPE: '当前会话没有该操作权限，请重新登录',
  WALLET_PROVISIONING: '个人钱包正在开通，请稍后刷新',
  IDEMPOTENCY_KEY_REUSED: '请求标识已用于其他操作，请刷新后重试',
  IMAGE_UPLOAD_INVALID: '图片无效或超过 5MB，请选择 JPG、PNG 或 WebP 图片',
  IMAGE_UPLOAD_UPSTREAM_UNAVAILABLE: '图片存储服务暂时不可用，请稍后重试'
};

async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = init.method?.toUpperCase() ?? 'GET';
  const csrf = !['GET', 'HEAD', 'OPTIONS'].includes(method)
    ? await fetch('/api/v1/csrf', { credentials: 'include' }).then(async response =>
        response.ok ? response.json() as Promise<{ headerName: string; token: string }> : undefined)
    : undefined;
  const response = await fetch(path, {
    ...init,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'X-Request-Id': createRequestId(),
      ...(csrf ? { [csrf.headerName]: csrf.token } : {}),
      ...init.headers
    }
  });
  const payload = response.status === 204
    ? undefined
    : await response.json().catch(() => undefined) as T | { code?: string; detail?: string } | undefined;
  if (!response.ok) {
    const code = payload && typeof payload === 'object' && 'code' in payload ? payload.code : undefined;
    const detail = payload && typeof payload === 'object' && 'detail' in payload ? payload.detail : undefined;
    if (response.status === 413) {
      throw new Error('图片超过服务器允许大小，请选择 5MB 以内的图片');
    }
    if ([502, 503, 504].includes(response.status)) {
      throw new Error('图片存储服务暂时不可用，请稍后重试');
    }
    throw new Error(code
      ? (problemText[code] ?? detail ?? `操作失败：${code}`)
      : (detail ?? `请求失败（${response.status}）`));
  }
  return payload as T;
}

const json = (body: unknown): RequestInit => ({
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body)
});
const mutate = (method: string, body?: unknown): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json', 'Idempotency-Key': createRequestId() },
  ...(body === undefined ? {} : { body: JSON.stringify(body) })
});
const base = (merchantId: string) => `/api/v1/merchant-gateway/merchant/merchants/${merchantId}`;

export const merchantApi = {
  session: () => requestJson<MerchantSession>('/api/v1/merchant-session'),
  logout: () => requestJson<void>('/api/v1/merchant-session', { method: 'DELETE' }),
  createCaptcha: () => requestJson<CaptchaChallenge>('/api/v1/merchant-auth/captcha', { method: 'POST' }),
  sendLoginCode: (mobile: string, captchaId: string, captchaCode: string) =>
    requestJson<{ challengeId: string; expiresAt: string; resendAt: string }>(
      '/api/v1/merchant-auth/code/send', { method: 'POST', ...json({ mobile, captchaId, captchaCode }) }),
  verifyLoginCode: (body: { challengeId: string; code: string; deviceId: string; resetPassword?: string }) =>
    requestJson<{ authenticated: boolean; expiresAt: string }>('/api/v1/merchant-auth/code/verify', {
      method: 'POST', ...json(body)
    }),
  verifyLoginPassword: (body: { mobile: string; password: string; captchaId: string; captchaCode: string; deviceId: string }) =>
    requestJson<{ authenticated: boolean; expiresAt: string }>('/api/v1/merchant-auth/password/verify', {
      method: 'POST', ...json(body)
    }),
  changePassword: (currentPassword: string, newPassword: string) =>
    requestJson<void>('/api/v1/merchant-auth/password', {
      method: 'PUT', ...json({ currentPassword, newPassword })
    }),
  setPassword: (newPassword: string) =>
    requestJson<void>('/api/v1/merchant-auth/password', {
      method: 'PUT', ...json({ currentPassword: '', newPassword })
    }),

  merchants: () => requestJson<Merchant[]>('/api/v1/merchant-gateway/merchant/merchants'),
  onboardings: () => requestJson<{ items: MerchantApply[]; page: number; size: number; total: number }>(
    '/api/v1/merchant-gateway/merchant/onboardings?page=0&size=100'),
  submitOnboarding: (body: Record<string, unknown>) =>
    requestJson<MerchantApply>('/api/v1/merchant-gateway/merchant/onboardings', mutate('POST', body)),
  resubmitOnboarding: (id: number, body: Record<string, unknown>) =>
    requestJson<MerchantApply>(`/api/v1/merchant-gateway/merchant/onboardings/${id}`, mutate('PUT', body)),

  initialize: (merchantId: string) => requestJson<{
    merchant: Merchant;
    defaultApplication: MerchantApplication;
    collectionCode: { codeId: string; status: string; qrContent: string } | null;
    appSecret: null;
  }>(`${base(merchantId)}/initialization`, mutate('POST')),
  dashboard: (merchantId: string, days: 7 | 30) =>
    requestJson<Dashboard>(`${base(merchantId)}/dashboard?days=${days}`),
  applications: (merchantId: string) => requestJson<MerchantApplication[]>(`${base(merchantId)}/applications`),
  application: (merchantId: string, appId: string) =>
    requestJson<MerchantApplication>(`${base(merchantId)}/applications/${encodeURIComponent(appId)}`),
  applyForApplication: (merchantId: string, name: string) =>
    requestJson<unknown>(`${base(merchantId)}/application-applies`, mutate('POST', { name })),
  updateApplication: (merchantId: string, appId: string, body: Record<string, unknown>) =>
    requestJson<MerchantApplication>(`${base(merchantId)}/applications/${encodeURIComponent(appId)}`, mutate('PATCH', body)),
  setApplicationStatus: (merchantId: string, appId: string, version: number, enabled: boolean) =>
    requestJson<MerchantApplication>(`${base(merchantId)}/applications/${encodeURIComponent(appId)}/status`, mutate('PUT', { version, enabled })),
  viewSecret: (merchantId: string, appId: string) =>
    requestJson<{ application: MerchantApplication; appSecret: string }>(`${base(merchantId)}/applications/${encodeURIComponent(appId)}/secret-view`, mutate('POST')),
  resetSecret: (merchantId: string, appId: string, version: number) =>
    requestJson<{ application: MerchantApplication; appSecret: null }>(`${base(merchantId)}/applications/${encodeURIComponent(appId)}/secret-reset`, mutate('POST', { version })),
  orders: (merchantId: string, query: { page?: number; size?: number; orderNo?: string; status?: string; channel?: string } = {}) => {
    const params = new URLSearchParams({
      page: String(query.page ?? 1),
      size: String(query.size ?? 20)
    });
    if (query.orderNo) params.set('orderNo', query.orderNo);
    if (query.status) params.set('status', query.status);
    if (query.channel) params.set('channel', query.channel);
    return requestJson<MerchantOrderPage>(`${base(merchantId)}/orders?${params}`);
  },
  order: (merchantId: string, paymentOrderNo: string) =>
    requestJson<MerchantOrder>(`${base(merchantId)}/orders/${encodeURIComponent(paymentOrderNo)}`),
  channelDistribution: (merchantId: string) =>
    requestJson<Array<{ appId: string; channel: string; orderCount: number; amountCent: number }>>(`${base(merchantId)}/orders/channel-distribution`),
  refund: (merchantId: string, order: MerchantOrder, reason: string) =>
    requestJson<unknown>(`${base(merchantId)}/orders/${encodeURIComponent(order.paymentOrderNo)}/refunds`, mutate('POST', {
      amountCent: order.amountCent, reason
    })),
  profile: (merchantId: string) => requestJson<Merchant>(`${base(merchantId)}/profile`),
  updateProfile: (merchantId: string, body: Record<string, unknown>) =>
    requestJson<Merchant>(`${base(merchantId)}/profile`, mutate('PATCH', body)),
  wallet: () => requestJson<WalletSummary>('/api/v1/merchant-wallet'),
  walletBills: () => requestJson<{ items: WalletBill[]; page: number; size: number; total: number }>(
    '/api/v1/merchant-wallet/bills?page=1&size=50'),

  // 浏览器同源上传到 BFF，由 BFF 完成 OSS PUT，避免 localhost 触发 OSS CORS。
  uploadImageFile: async (file: File) => {
    const controller = new AbortController();
    const timeout = globalThis.setTimeout(() => controller.abort(), 30_000);
    try {
      return await requestJson<{ objectKey: string }>(
        '/api/v1/merchant-image-uploads', {
          method: 'POST',
          headers: {
            'Content-Type': file.type || 'image/jpeg',
            'X-File-Name': encodeURIComponent(file.name)
          },
          body: file,
          signal: controller.signal
        });
    } catch (error) {
      if (controller.signal.aborted) {
        throw new Error('图片上传超时，请检查网络后重试');
      }
      throw error;
    } finally {
      globalThis.clearTimeout(timeout);
    }
  },
  getImageReadUrls: (objectKeys: string[]) =>
    requestJson<{ urls: Record<string, string> }>(
      '/api/v1/merchant-gateway/merchant/image-read-urls', mutate('POST', { objectKeys }))
};

export const zh = {
  status: (value?: string) => ({
    ACTIVE: '正常', DISABLED: '已停用', FROZEN: '已冻结', ENABLED: '已启用',
    PENDING: '审核中', APPROVED: '已通过', REJECTED: '已驳回', SUPPLEMENT: '待补充',
    SUCCEEDED: '成功', PROCESSING: '处理中', FAILED: '失败', REFUNDED: '已退款'
  }[value ?? ''] ?? value ?? '-'),
  channel: (value?: string) => ({
    WALLET: 'MiniPay 余额', WALLET_BALANCE: 'MiniPay 余额', ALIPAY: '支付宝沙箱',
    WECHAT: '微信沙箱', WECHAT_PAY: '微信沙箱'
  }[value ?? ''] ?? value ?? '-'),
  businessType: (value?: string) => ({
    OPENING_GRANT: '开户赠金', PAYMENT: '消费支付', MERCHANT_PAYMENT: '商户收付款',
    REFUND: '消费退款', MERCHANT_REFUND: '商户退款', RECHARGE: '充值',
    WITHDRAWAL: '提现', TRANSFER: '转账'
  }[value ?? ''] ?? value ?? '-')
};
