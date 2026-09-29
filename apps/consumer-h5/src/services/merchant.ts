import { createRequestId } from '@minipay/shared';
import { asArray, asRecord, readBoolean, readPage, readString } from './parsers';
import { httpRequest } from './http';

export interface ConsumerMerchant {
  merchantId: string;
  merchantNo: string;
  name: string;
  status: string;
  initialized: boolean;
}

export interface MerchantOnboarding {
  id: number;
  shopName: string;
  applyStatus: string;
  rejectReason?: string;
  resultantMerchantId?: string;
  version: number;
  updatedAt?: string;
}

export interface MerchantOnboardingInput {
  shopName: string;
  address: string;
  latitude: number;
  longitude: number;
  shopImages: string;
  contactName: string;
  contactMobile: string;
  remark?: string;
  version?: number;
}

export interface BusinessCollectionCode {
  merchantId: string;
  merchantName: string;
  status: string;
  code: string;
}

function parseMerchant(raw: unknown): ConsumerMerchant {
  const record = asRecord(raw);
  return {
    merchantId: readString(record, 'merchantId') ?? '',
    merchantNo: readString(record, 'merchantNo') ?? '',
    name: readString(record, 'name', 'shortName') ?? '我的商户',
    status: readString(record, 'status') ?? 'UNKNOWN',
    initialized: readBoolean(record, 'initialized') ?? false
  };
}

function parseOnboarding(raw: unknown): MerchantOnboarding {
  const record = asRecord(raw);
  return {
    id: Number(record.id ?? 0),
    shopName: readString(record, 'shopName') ?? '商户申请',
    applyStatus: readString(record, 'applyStatus') ?? 'UNKNOWN',
    rejectReason: readString(record, 'rejectReason'),
    resultantMerchantId: readString(record, 'resultantMerchantId'),
    version: Number(record.version ?? 0),
    updatedAt: readString(record, 'updatedAt')
  };
}

export async function fetchMerchantCenter(): Promise<{
  merchants: ConsumerMerchant[];
  onboardings: MerchantOnboarding[];
}> {
  const [merchantRaw, onboardingRaw] = await Promise.all([
    httpRequest<unknown>('/api/v1/merchant-center/merchants', { method: 'GET' }),
    httpRequest<unknown>('/api/v1/merchant-center/onboardings?size=20', { method: 'GET' })
  ]);
  const onboardingPage = Array.isArray(onboardingRaw) ? { items: asArray(onboardingRaw) } : readPage(onboardingRaw);
  return {
    merchants: asArray(merchantRaw).map(parseMerchant).filter((item) => item.merchantId),
    onboardings: onboardingPage.items.map(parseOnboarding).filter((item) => item.id > 0)
  };
}

function onboardingBody(input: MerchantOnboardingInput) {
  return {
    merchantType: 'INDIVIDUAL',
    shopName: input.shopName,
    mccCode: '5999',
    address: input.address,
    latitude: input.latitude,
    longitude: input.longitude,
    shopImages: input.shopImages,
    contactName: input.contactName,
    contactMobile: input.contactMobile,
    remark: input.remark ?? ''
  };
}

export async function submitMerchantOnboarding(input: MerchantOnboardingInput): Promise<void> {
  await httpRequest('/api/v1/merchant-center/onboardings', {
    method: 'POST',
    headers: { 'Idempotency-Key': createRequestId() },
    data: onboardingBody(input)
  });
}

export async function resubmitMerchantOnboarding(
  applyId: number,
  input: MerchantOnboardingInput
): Promise<void> {
  await httpRequest(`/api/v1/merchant-center/onboardings/${applyId}`, {
    method: 'PUT',
    headers: { 'Idempotency-Key': createRequestId() },
    data: { ...onboardingBody(input), version: input.version ?? 0 }
  });
}

export async function initializeMerchant(merchantId: string): Promise<void> {
  await httpRequest(`/api/v1/merchant-center/merchants/${encodeURIComponent(merchantId)}/initialization`, {
    method: 'POST',
    headers: { 'Idempotency-Key': createRequestId() },
    data: {}
  });
}

export async function fetchBusinessCollectionCode(): Promise<BusinessCollectionCode> {
  const raw = asRecord(await httpRequest<unknown>('/api/v1/merchant-center/collection-code', { method: 'GET' }));
  const merchant = asRecord(raw.merchant);
  const code = asRecord(raw.collectionCode);
  return {
    merchantId: readString(merchant, 'merchantId') ?? '',
    merchantName: readString(merchant, 'name', 'shortName') ?? '我的商户',
    status: readString(code, 'status') ?? 'UNKNOWN',
    code: readString(code, 'qrContent') ?? ''
  };
}

export async function uploadMerchantImage(file: File): Promise<string> {
  const raw = await httpRequest<unknown>('/api/v1/merchant-center/image-files', {
    method: 'POST',
    timeout: 30_000,
    headers: {
      'Content-Type': file.type || 'image/jpeg',
      'X-File-Name': encodeURIComponent(file.name)
    },
    data: await file.arrayBuffer()
  });
  const objectKey = readString(asRecord(raw), 'objectKey');
  if (!objectKey) throw new Error('店铺图片上传响应缺少 objectKey');
  return objectKey;
}
