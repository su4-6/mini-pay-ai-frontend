import type {
  ApplicationApplyStatus,
  ApplicationStatus,
  ImageReadUrlsResponse,
  ImageUploadGrant,
  ImageUploadRequest,
  MerchantApplyStatus,
  MerchantStatus,
  MerchantType,
  OpsApplication,
  OpsApplicationApply,
  OpsApplicationApplyPage,
  OpsApplicationPage,
  OpsApplicationSummary,
  OpsDashboard,
  OpsDashboardRange,
  OpsMerchant,
  OpsMerchantApply,
  OpsMerchantApplyPage,
  OpsMerchantPage,
  OpsNotification,
  OpsNotificationDetail,
  OpsPaymentOrderDetail,
  OpsPaymentOrderPage,
  OpsRefundDetail,
  OpsRefundPage,
  OpsTransferDetail,
  OpsTransferPage,
  OpsRechargeDetail,
  OpsRechargePage,
  OpsWithdrawalDetail,
  OpsWithdrawalPage
} from '@minipay/api-contracts';
import {
  createIdempotencyKey,
  IDEMPOTENCY_KEY_HEADER,
  requestJson
} from '@minipay/api-client';
import { getCsrf } from './auth';

export interface MerchantFilters {
  page: number;
  size: number;
  merchantNo?: string;
  name?: string;
  contactMobile?: string;
  status?: MerchantStatus;
}

export function getOpsDashboard(range: OpsDashboardRange): Promise<OpsDashboard> {
  return requestJson<OpsDashboard>(`/api/v1/ops/dashboard?range=${range}`);
}

export function getMerchants(filters: MerchantFilters): Promise<OpsMerchantPage> {
  const query = new URLSearchParams({
    page: String(filters.page),
    size: String(filters.size)
  });
  if (filters.merchantNo?.trim()) query.set('merchantNo', filters.merchantNo.trim());
  if (filters.name?.trim()) query.set('name', filters.name.trim());
  if (filters.contactMobile?.trim()) query.set('contactMobile', filters.contactMobile.trim());
  if (filters.status) query.set('status', filters.status);
  return requestJson<OpsMerchantPage>(`/api/v1/ops/merchants?${query}`);
}

export function getMerchant(merchantId: string): Promise<OpsMerchant> {
  return requestJson<OpsMerchant>(`/api/v1/ops/merchants/${merchantId}`);
}

async function mutateMerchant<T>(
  url: string,
  method: 'POST' | 'PATCH' | 'DELETE',
  body: unknown,
  options?: { idempotencyKey?: string; ifMatch?: number }
): Promise<T> {
  const csrf = await getCsrf();
  return requestJson<T>(url, {
    method,
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
      [IDEMPOTENCY_KEY_HEADER]: options?.idempotencyKey ?? createIdempotencyKey(),
      ...(options?.ifMatch === undefined ? {} : { 'If-Match': `"${options.ifMatch}"` })
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
}

export interface CreateMerchantInput {
  name: string;
  shortName: string;
  contactName: string;
  contactMobile: string;
  contactEmail?: string;
  remark?: string;
  merchantType: MerchantType;
  mccCode?: string;
  address?: string;
  latitude?: number;
  longitude?: number;
  shopImages?: string;
  status?: MerchantStatus;
}

export function uploadImage(input: ImageUploadRequest): Promise<ImageUploadGrant> {
  return mutateMerchant('/api/v1/ops/image-uploads', 'POST', input);
}

export async function uploadImageFile(file: File): Promise<{ objectKey: string }> {
  const csrf = await getCsrf();
  return requestJson<{ objectKey: string }>('/api/v1/ops-image-uploads', {
    method: 'POST',
    headers: {
      'Content-Type': file.type || 'image/jpeg',
      'X-File-Name': encodeURIComponent(file.name),
      [csrf.headerName]: csrf.token
    },
    body: file
  });
}

export function getImageReadUrls(objectKeys: string[]): Promise<ImageReadUrlsResponse> {
  return mutateMerchant('/api/v1/ops/image-read-urls', 'POST', { objectKeys });
}

export type UpdateMerchantInput = Omit<CreateMerchantInput, 'status'>;

export function createMerchant(
  input: CreateMerchantInput,
  idempotencyKey?: string
): Promise<OpsMerchant> {
  return mutateMerchant('/api/v1/ops/merchants', 'POST', input, { idempotencyKey });
}

export function updateMerchant(
  merchant: Pick<OpsMerchant, 'merchantId' | 'version'>,
  input: UpdateMerchantInput,
  idempotencyKey?: string
): Promise<OpsMerchant> {
  return mutateMerchant(
    `/api/v1/ops/merchants/${merchant.merchantId}`,
    'PATCH',
    { ...input, version: merchant.version },
    { idempotencyKey }
  );
}

export function changeMerchantStatus(
  merchant: Pick<OpsMerchant, 'merchantId' | 'version'>,
  status: MerchantStatus,
  idempotencyKey?: string
): Promise<OpsMerchant> {
  const action = status === 'ACTIVE' ? 'enable' : 'disable';
  return mutateMerchant(
    `/api/v1/ops/merchants/${merchant.merchantId}/${action}`,
    'POST',
    { version: merchant.version },
    { idempotencyKey }
  );
}

export function freezeMerchant(
  merchant: Pick<OpsMerchant, 'merchantId' | 'version'>,
  reason: string,
  idempotencyKey?: string
): Promise<OpsMerchant> {
  return mutateMerchant(
    `/api/v1/ops/merchants/${merchant.merchantId}/freeze`,
    'POST',
    { version: merchant.version, reason },
    { idempotencyKey }
  );
}

export function unfreezeMerchant(
  merchant: Pick<OpsMerchant, 'merchantId' | 'version'>,
  idempotencyKey?: string
): Promise<OpsMerchant> {
  return mutateMerchant(
    `/api/v1/ops/merchants/${merchant.merchantId}/unfreeze`,
    'POST',
    { version: merchant.version },
    { idempotencyKey }
  );
}

export function deleteMerchant(
  merchant: Pick<OpsMerchant, 'merchantId' | 'version'>,
  idempotencyKey?: string
): Promise<void> {
  return mutateMerchant(
    `/api/v1/ops/merchants/${merchant.merchantId}`,
    'DELETE',
    undefined,
    { idempotencyKey, ifMatch: merchant.version }
  );
}

export interface MerchantApplyFilters {
  page: number;
  size: number;
  applyStatus?: MerchantApplyStatus;
}

export function getMerchantApplies(
  filters: MerchantApplyFilters
): Promise<OpsMerchantApplyPage> {
  const query = new URLSearchParams({
    page: String(filters.page),
    size: String(filters.size)
  });
  if (filters.applyStatus) query.set('applyStatus', filters.applyStatus);
  return requestJson<OpsMerchantApplyPage>(`/api/v1/ops/merchant-applies?${query}`);
}

export function getMerchantApply(applyId: number): Promise<OpsMerchantApply> {
  return requestJson<OpsMerchantApply>(`/api/v1/ops/merchant-applies/${applyId}`);
}

export function approveMerchantApply(
  apply: Pick<OpsMerchantApply, 'id' | 'version'>,
  idempotencyKey?: string
): Promise<OpsMerchantApply> {
  return mutateMerchant(
    `/api/v1/ops/merchant-applies/${apply.id}/approve`,
    'POST',
    { version: apply.version },
    { idempotencyKey }
  );
}

export function rejectMerchantApply(
  apply: Pick<OpsMerchantApply, 'id' | 'version'>,
  reason: string,
  idempotencyKey?: string
): Promise<OpsMerchantApply> {
  return mutateMerchant(
    `/api/v1/ops/merchant-applies/${apply.id}/reject`,
    'POST',
    { version: apply.version, reason },
    { idempotencyKey }
  );
}

export function requestSupplementMerchantApply(
  apply: Pick<OpsMerchantApply, 'id' | 'version'>,
  reason: string,
  idempotencyKey?: string
): Promise<OpsMerchantApply> {
  return mutateMerchant(
    `/api/v1/ops/merchant-applies/${apply.id}/request-supplement`,
    'POST',
    { version: apply.version, reason },
    { idempotencyKey }
  );
}

export interface ApplicationApplyFilters {
  page: number;
  size: number;
  applyStatus?: ApplicationApplyStatus;
}

export function getApplicationApplies(
  filters: ApplicationApplyFilters
): Promise<OpsApplicationApplyPage> {
  const query = new URLSearchParams({
    page: String(filters.page),
    size: String(filters.size)
  });
  if (filters.applyStatus) query.set('applyStatus', filters.applyStatus);
  return requestJson<OpsApplicationApplyPage>(`/api/v1/ops/application-applies?${query}`);
}

export function getApplicationApply(applyId: number): Promise<OpsApplicationApply> {
  return requestJson<OpsApplicationApply>(`/api/v1/ops/application-applies/${applyId}`);
}

export function submitApplicationApply(
  input: { merchantId: string; name: string },
  idempotencyKey?: string
): Promise<OpsApplicationApply> {
  return mutateMerchant(
    '/api/v1/ops/application-applies',
    'POST',
    input,
    { idempotencyKey }
  );
}

export function resubmitApplicationApply(
  apply: Pick<OpsApplicationApply, 'id' | 'version'>,
  name: string,
  idempotencyKey?: string
): Promise<OpsApplicationApply> {
  return mutateMerchant(
    `/api/v1/ops/application-applies/${apply.id}/submit`,
    'POST',
    { name, version: apply.version },
    { idempotencyKey }
  );
}

export function approveApplicationApply(
  apply: Pick<OpsApplicationApply, 'id' | 'version'>,
  idempotencyKey?: string
): Promise<OpsApplicationApply> {
  return mutateMerchant(
    `/api/v1/ops/application-applies/${apply.id}/approve`,
    'POST',
    { version: apply.version },
    { idempotencyKey }
  );
}

export function rejectApplicationApply(
  apply: Pick<OpsApplicationApply, 'id' | 'version'>,
  reason: string,
  idempotencyKey?: string
): Promise<OpsApplicationApply> {
  return mutateMerchant(
    `/api/v1/ops/application-applies/${apply.id}/reject`,
    'POST',
    { version: apply.version, reason },
    { idempotencyKey }
  );
}

export function requestSupplementApplicationApply(
  apply: Pick<OpsApplicationApply, 'id' | 'version'>,
  reason: string,
  idempotencyKey?: string
): Promise<OpsApplicationApply> {
  return mutateMerchant(
    `/api/v1/ops/application-applies/${apply.id}/request-supplement`,
    'POST',
    { version: apply.version, reason },
    { idempotencyKey }
  );
}

export interface ApplicationFilters {
  page: number;
  size: number;
  appId?: string;
  name?: string;
  merchantId?: string;
  status?: ApplicationStatus;
  unavailable?: boolean;
}

export function getApplications(filters: ApplicationFilters): Promise<OpsApplicationPage> {
  const query = new URLSearchParams({
    page: String(filters.page),
    size: String(filters.size)
  });
  if (filters.appId?.trim()) query.set('appId', filters.appId.trim());
  if (filters.name?.trim()) query.set('name', filters.name.trim());
  if (filters.merchantId) query.set('merchantId', filters.merchantId);
  if (filters.status) query.set('status', filters.status);
  if (filters.unavailable) query.set('unavailable', 'true');
  return requestJson<OpsApplicationPage>(`/api/v1/ops/applications?${query}`);
}

export function getApplicationSummary(): Promise<OpsApplicationSummary> {
  return requestJson<OpsApplicationSummary>('/api/v1/ops/applications/summary');
}

/** 分页拉取当前筛选条件下的全量应用（每次 100 条，最多 1000 条），供 CSV 导出使用。 */
export async function fetchAllForExport(filters: ApplicationFilters): Promise<{
  items: OpsApplication[];
  total: number;
  truncated: boolean;
}> {
  const all: OpsApplication[] = [];
  const maxPages = 10;
  let total = 0;
  for (let page = 0; page < maxPages; page++) {
    const result = await getApplications({ ...filters, page, size: 100 });
    total = result.total;
    all.push(...result.items);
    if (all.length >= result.total || result.items.length < 100) break;
  }
  return { items: all, total, truncated: all.length < total };
}

export function getApplication(applicationId: string): Promise<OpsApplication> {
  return requestJson<OpsApplication>(`/api/v1/ops/applications/${applicationId}`);
}

export interface CreateApplicationInput {
  merchantId: string;
  name: string;
  status: ApplicationStatus;
}

export function createApplication(
  input: CreateApplicationInput,
  idempotencyKey?: string
): Promise<OpsApplication> {
  return mutateMerchant('/api/v1/ops/applications', 'POST', input, { idempotencyKey });
}

export function updateApplication(
  application: Pick<OpsApplication, 'applicationId' | 'version'>,
  name: string,
  idempotencyKey?: string
): Promise<OpsApplication> {
  return mutateMerchant(
    `/api/v1/ops/applications/${application.applicationId}`,
    'PATCH',
    { name, version: application.version },
    { idempotencyKey }
  );
}

export function changeApplicationStatus(
  application: Pick<OpsApplication, 'applicationId' | 'version'>,
  status: ApplicationStatus,
  idempotencyKey?: string
): Promise<OpsApplication> {
  const action = status === 'ACTIVE' ? 'enable' : 'disable';
  return mutateMerchant(
    `/api/v1/ops/applications/${application.applicationId}/${action}`,
    'POST',
    { version: application.version },
    { idempotencyKey }
  );
}

export function deleteApplication(
  application: Pick<OpsApplication, 'applicationId' | 'version'>,
  idempotencyKey?: string
): Promise<void> {
  return mutateMerchant(
    `/api/v1/ops/applications/${application.applicationId}`,
    'DELETE',
    undefined,
    { idempotencyKey, ifMatch: application.version }
  );
}

// ── 运营端订单查询（OPS-05/06/07/08）──
export interface PaymentOrderFilters {
  page: number;
  size: number;
  merchantNo?: string;
  name?: string;
  appId?: string;
  status?: string;
  from?: string;
  to?: string;
}

export interface RefundOrderFilters {
  page: number;
  size: number;
  merchantNo?: string;
  name?: string;
  status?: string;
  from?: string;
  to?: string;
}

export interface TransferOrderFilters {
  page: number;
  size: number;
  transferNo?: string;
  status?: string;
  from?: string;
  to?: string;
}

export interface ConsumerFundsOrderFilters {
  page: number;
  size: number;
  orderNo?: string;
  status?: string;
  from?: string;
  to?: string;
}

function buildOrderQuery(
  filters: { page: number; size: number },
  extra: Record<string, string | undefined>
): string {
  const query = new URLSearchParams({ page: String(filters.page), size: String(filters.size) });
  Object.entries(extra).forEach(([key, value]) => {
    if (value && value.trim()) query.set(key, value.trim());
  });
  return query.toString();
}

export function getOpsPayments(
  filters: PaymentOrderFilters
): Promise<OpsPaymentOrderPage> {
  return requestJson<OpsPaymentOrderPage>(
    `/api/v1/ops/payments?${buildOrderQuery(filters, {
      merchantNo: filters.merchantNo,
      name: filters.name,
      appId: filters.appId,
      status: filters.status,
      from: filters.from,
      to: filters.to
    })}`
  );
}

export function getOpsPayment(paymentOrderNo: string): Promise<OpsPaymentOrderDetail> {
  return requestJson<OpsPaymentOrderDetail>(`/api/v1/ops/payments/${paymentOrderNo}`);
}

export function getOpsRefunds(filters: RefundOrderFilters): Promise<OpsRefundPage> {
  return requestJson<OpsRefundPage>(
    `/api/v1/ops/refunds?${buildOrderQuery(filters, {
      merchantNo: filters.merchantNo,
      name: filters.name,
      status: filters.status,
      from: filters.from,
      to: filters.to
    })}`
  );
}

export function getOpsRefund(refundOrderNo: string): Promise<OpsRefundDetail> {
  return requestJson<OpsRefundDetail>(`/api/v1/ops/refunds/${refundOrderNo}`);
}

export function getOpsTransfers(filters: TransferOrderFilters): Promise<OpsTransferPage> {
  return requestJson<OpsTransferPage>(
    `/api/v1/ops/transfers?${buildOrderQuery(filters, {
      transferNo: filters.transferNo,
      status: filters.status,
      from: filters.from,
      to: filters.to
    })}`
  );
}

export function getOpsTransfer(transferOrderNo: string): Promise<OpsTransferDetail> {
  return requestJson<OpsTransferDetail>(`/api/v1/ops/transfers/${transferOrderNo}`);
}

export function getOpsRecharges(filters: ConsumerFundsOrderFilters): Promise<OpsRechargePage> {
  return requestJson<OpsRechargePage>(
    `/api/v1/ops/recharges?${buildOrderQuery(filters, {
      rechargeNo: filters.orderNo, status: filters.status, from: filters.from, to: filters.to
    })}`
  );
}

export function getOpsRecharge(orderNo: string): Promise<OpsRechargeDetail> {
  return requestJson<OpsRechargeDetail>(`/api/v1/ops/recharges/${encodeURIComponent(orderNo)}`);
}

export function getOpsWithdrawals(filters: ConsumerFundsOrderFilters): Promise<OpsWithdrawalPage> {
  return requestJson<OpsWithdrawalPage>(
    `/api/v1/ops/withdrawals?${buildOrderQuery(filters, {
      withdrawalNo: filters.orderNo, status: filters.status, from: filters.from, to: filters.to
    })}`
  );
}

export function getOpsWithdrawal(orderNo: string): Promise<OpsWithdrawalDetail> {
  return requestJson<OpsWithdrawalDetail>(`/api/v1/ops/withdrawals/${encodeURIComponent(orderNo)}`);
}

export interface FoodOrderFilters {
  page: number;
  size: number;
  orderNo?: string;
  paymentStatus?: string;
  fulfillmentStatus?: string;
  refundStatus?: string;
  from?: string;
  to?: string;
}

export function getOpsFoodOrders(filters: FoodOrderFilters): Promise<import('@minipay/api-contracts').OpsFoodOrderPage> {
  return requestJson(`/api/v1/ops/food-orders?${buildOrderQuery(filters, {
    orderNo: filters.orderNo,
    paymentStatus: filters.paymentStatus,
    fulfillmentStatus: filters.fulfillmentStatus,
    refundStatus: filters.refundStatus,
    from: filters.from,
    to: filters.to
  })}`);
}

export function getOpsFoodOrder(orderRefId: string): Promise<import('@minipay/api-contracts').OpsFoodOrder> {
  return requestJson(`/api/v1/ops/food-orders/${encodeURIComponent(orderRefId)}`);
}

export interface CollectionRecordFilters {
  page: number;
  size: number;
  ownerId?: string;
  businessNo?: string;
  status?: string;
  type?: string;
  from?: string;
  to?: string;
}

export function getOpsCollectionRecords(filters: CollectionRecordFilters): Promise<import('@minipay/api-contracts').OpsCollectionRecordPage> {
  return requestJson(`/api/v1/ops/collection-records?${buildOrderQuery(filters, {
    ownerId: filters.ownerId,
    businessNo: filters.businessNo,
    status: filters.status,
    type: filters.type,
    from: filters.from,
    to: filters.to
  })}`);
}

export function getOpsCollectionRecord(billId: string): Promise<import('@minipay/api-contracts').OpsCollectionRecord> {
  return requestJson(`/api/v1/ops/collection-records/${encodeURIComponent(billId)}`);
}

export function getOpsNotifications(size = 20): Promise<OpsNotification[]> {
  return requestJson<OpsNotification[]>(`/api/v1/ops/notifications?size=${size}`);
}

export function getOpsNotification(notificationId: string): Promise<OpsNotificationDetail> {
  return requestJson<OpsNotificationDetail>(`/api/v1/ops/notifications/${notificationId}`);
}

export function retryNotification(notificationId: string): Promise<void> {
  return mutateMerchant(`/api/v1/ops/notifications/${notificationId}/retry`, 'POST', undefined);
}
