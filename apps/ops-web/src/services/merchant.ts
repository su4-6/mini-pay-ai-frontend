import { createRequestId } from '@minipay/shared';
import { requestJson } from './auth';

export interface OpsMerchant {
  merchantId: string;
  name: string;
  category: string;
  contactName: string | null;
  contactMobileMasked: string | null;
  status: 'PENDING' | 'ACTIVE' | 'REJECTED' | 'DISABLED';
  receiveLocked: boolean;
  remark: string | null;
  version: number;
  createdAt: string;
}

export interface OpsOnboarding {
  onboardingId: string;
  merchantId: string;
  merchantName: string;
  category: string;
  contactName: string | null;
  contactMobileMasked: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  rejectReason: string | null;
  reviewerId: string | null;
  reviewedAt: string | null;
  version: number;
  createdAt: string;
}

export interface OpsNotification {
  notificationId: string;
  eventId: string;
  type: 'PAYMENT' | 'REFUND';
  status: 'PENDING' | 'RETRYING' | 'DELIVERING' | 'SUCCEEDED' | 'FAILED';
  attempts: number;
  nextAttemptAt: string | null;
  responseSummary: string | null;
  createdAt: string;
}

function options(method: string, body?: unknown): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json', 'Idempotency-Key': createRequestId() }, body: body === undefined ? undefined : JSON.stringify(body) };
}

export const opsMerchantApi = {
  merchants: (status?: string) => requestJson<OpsMerchant[]>(`/api/v1/ops-gateway/merchants${status ? `?status=${encodeURIComponent(status)}` : ''}`),
  createMerchant: (body: { ownerUserId: string; merchantName: string; category: string; contactName?: string; contactMobile?: string; agreementVersion?: string; remark?: string }) => requestJson<OpsMerchant>('/api/v1/ops-gateway/merchants', options('POST', body)),
  updateMerchant: (merchantId: string, body: Pick<OpsMerchant, 'version' | 'name' | 'remark' | 'status' | 'receiveLocked'>) => requestJson<OpsMerchant>(`/api/v1/ops-gateway/merchants/${encodeURIComponent(merchantId)}`, options('PATCH', body)),
  onboardings: (status?: string) => requestJson<OpsOnboarding[]>(`/api/v1/ops-gateway/onboardings${status ? `?status=${encodeURIComponent(status)}` : ''}`),
  approve: (id: string) => requestJson<void>(`/api/v1/ops-gateway/onboardings/${id}/approve`, options('POST')),
  reject: (id: string, reason: string) => requestJson<void>(`/api/v1/ops-gateway/onboardings/${id}/reject`, options('POST', { reason })),
  notifications: () => requestJson<OpsNotification[]>('/api/v1/ops/notifications'),
  retryNotification: (id: string) => requestJson<void>(`/api/v1/ops/notifications/${id}/retry`, options('POST')),
  todos: () => requestJson<{ pendingOnboardings: number; notificationsAwaitingManualRetry: number }>('/api/v1/ops-gateway/todos')
};
