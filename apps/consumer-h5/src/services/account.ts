import { createRequestId } from '@minipay/shared';
import type {
  AccountSecurityOverview,
  ConsumerCapabilities,
  ConsumerProfileDetail,
  RealNameVerification,
  VerificationChallenge
} from '../types/consumer';
import { asRecord, readBoolean, readFen, readString } from './parsers';
import { httpRequest } from './http';

function idempotencyHeaders(): Record<string, string> {
  return { 'Idempotency-Key': createRequestId() };
}

function parseProfile(raw: unknown): ConsumerProfileDetail {
  const record = asRecord(raw);
  return {
    userId: readString(record, 'userId') ?? '',
    nickname: readString(record, 'nickname') ?? 'MiniPay 用户',
    miniPayNo: readString(record, 'miniPayNo') ?? '',
    avatarUrl: readString(record, 'avatarUrl'),
    version: readFen(record, 'version') ?? 0,
    legalNameMasked: readString(record, 'legalNameMasked')
  };
}

export async function fetchProfile(): Promise<ConsumerProfileDetail> {
  return parseProfile(await httpRequest<unknown>('/api/v1/users/me', { method: 'GET' }));
}

export async function updateProfile(nickname: string, version: number): Promise<ConsumerProfileDetail> {
  return parseProfile(await httpRequest<unknown>('/api/v1/users/me', {
    method: 'PATCH',
    data: { nickname, version }
  }));
}

export async function completeOnboarding(nickname: string): Promise<void> {
  await httpRequest('/api/v1/users/me/onboarding', {
    method: 'PUT',
    headers: idempotencyHeaders(),
    data: { nickname }
  });
}

export async function fetchCapabilities(): Promise<ConsumerCapabilities> {
  const record = asRecord(await httpRequest<unknown>('/api/v1/users/me/capabilities', { method: 'GET' }));
  return {
    onboardingCompleted: readBoolean(record, 'onboardingCompleted') ?? false,
    realNameStatus: readString(record, 'realNameStatus') ?? 'UNVERIFIED',
    realNameVerified: readBoolean(record, 'realNameVerified') ?? false,
    payPasswordSet: readBoolean(record, 'payPasswordSet') ?? false
  };
}

export async function submitRealName(input: {
  legalName: string;
  idNumber: string;
  faceImage: File;
}): Promise<RealNameVerification> {
  const form = new FormData();
  form.append('legalName', input.legalName);
  form.append('idNumber', input.idNumber);
  form.append('faceImage', input.faceImage, input.faceImage.name || 'face.jpg');
  const record = asRecord(await httpRequest<unknown>('/api/v1/real-name-verifications', {
    method: 'POST',
    headers: idempotencyHeaders(),
    data: form,
    timeout: 30_000
  }));
  return {
    verificationId: readString(record, 'verificationId') ?? '',
    status: readString(record, 'status') ?? 'PENDING',
    legalNameMasked: readString(record, 'legalNameMasked'),
    idNumberMasked: readString(record, 'idNumberMasked'),
    failureCode: readString(record, 'failureCode')
  };
}

export async function fetchAccountSecurity(): Promise<AccountSecurityOverview> {
  const record = asRecord(await httpRequest<unknown>('/api/v1/users/me/account-security', { method: 'GET' }));
  return {
    maskedMobile: readString(record, 'maskedMobile') ?? '',
    maskedEmail: readString(record, 'maskedEmail'),
    paymentPasswordSet: readBoolean(record, 'paymentPasswordSet') ?? false
  };
}

function parseChallenge(raw: unknown): VerificationChallenge {
  const record = asRecord(raw);
  return {
    challengeId: readString(record, 'challengeId') ?? '',
    maskedTarget: readString(record, 'maskedTarget', 'maskedMobile'),
    expiresAt: readString(record, 'expiresAt'),
    demoCode: readString(record, 'demoCode')
  };
}

export async function requestPhoneChange(mobile: string): Promise<VerificationChallenge> {
  return parseChallenge(await httpRequest('/api/v1/users/me/phone-change-challenges', {
    method: 'POST', headers: idempotencyHeaders(), data: { mobile }
  }));
}

export async function confirmPhoneChange(mobile: string, challengeId: string, code: string): Promise<void> {
  await httpRequest('/api/v1/users/me/phone', {
    method: 'PUT', headers: idempotencyHeaders(), data: { mobile, challengeId, code }
  });
}

export async function requestPaymentPasswordChange(mobile: string): Promise<VerificationChallenge> {
  return parseChallenge(await httpRequest('/api/v1/users/me/payment-password-change-challenges', {
    method: 'POST', headers: idempotencyHeaders(), data: { mobile }
  }));
}

export async function verifyPaymentPasswordChange(challengeId: string, code: string): Promise<string> {
  const record = asRecord(await httpRequest<unknown>(
    `/api/v1/users/me/payment-password-change-challenges/${encodeURIComponent(challengeId)}/verifications`,
    { method: 'POST', headers: idempotencyHeaders(), data: { code } }
  ));
  return readString(record, 'verificationToken') ?? '';
}

export async function changePaymentPassword(verificationToken: string, newPassword: string): Promise<void> {
  await httpRequest('/api/v1/users/me/payment-password-changes', {
    method: 'POST', headers: idempotencyHeaders(), data: { verificationToken, newPassword }
  });
}
