import type { ProblemDetails } from '@minipay/api-contracts';

export const REQUEST_ID_HEADER = 'X-Request-Id';
export const IDEMPOTENCY_KEY_HEADER = 'Idempotency-Key';

export class ApiProblemError extends Error {
  constructor(public readonly problem: ProblemDetails) {
    super(problem.detail ?? problem.title);
    this.name = 'ApiProblemError';
  }
}

export function isProblemDetails(value: unknown): value is ProblemDetails {
  if (!value || typeof value !== 'object') return false;
  const problem = value as Partial<ProblemDetails>;
  return (
    typeof problem.type === 'string' &&
    typeof problem.title === 'string' &&
    typeof problem.status === 'number' &&
    typeof problem.code === 'string' &&
    typeof problem.requestId === 'string'
  );
}

export function toApiProblemError(payload: ProblemDetails): ApiProblemError {
  return new ApiProblemError(payload);
}

export function createIdempotencyKey(): string {
  return crypto.randomUUID();
}

export async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      [REQUEST_ID_HEADER]: crypto.randomUUID(),
      ...init?.headers
    }
  });
  const text = await response.text();
  let payload: T | ProblemDetails | undefined;
  try {
    payload = text ? JSON.parse(text) as T | ProblemDetails : undefined;
  } catch {
    payload = undefined;
  }
  if (!response.ok) {
    const problem = payload as ProblemDetails | undefined;
    throw new ApiProblemError(problem ?? {
      type: 'about:blank',
      title: response.statusText || 'Request failed',
      status: response.status,
      code: 'UNEXPECTED_RESPONSE',
      requestId: response.headers.get(REQUEST_ID_HEADER) ?? 'unknown'
    });
  }
  return payload as T;
}
