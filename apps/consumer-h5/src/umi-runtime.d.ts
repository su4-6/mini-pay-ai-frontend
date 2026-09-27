import type {} from '@umijs/max';

declare global {
  const MINIPAY_PUBLIC_PATH: string;
  const CONSUMER_BFF_PUBLIC_PATH: string;
}

/**
 * Umi Request 的调用签名。这里只声明本项目实际使用的字段，
 * 避免为了类型依赖把 axios 提升为直接依赖。
 */
export interface ConsumerRequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'HEAD' | 'OPTIONS';
  data?: unknown;
  params?: Record<string, string | number | boolean | undefined>;
  headers?: Record<string, string>;
  timeout?: number;
  withCredentials?: boolean;
  signal?: AbortSignal;
  getResponse?: boolean;
  responseType?: 'json' | 'text';
}

export interface ConsumerResponse<TData> {
  data: TData;
  status: number;
  statusText: string;
  /** axios 的 AxiosHeaders 实例，只能按鸭子类型读取，因此不臆造结构。 */
  headers: unknown;
}

export interface ConsumerRequest {
  (url: string, options: ConsumerRequestOptions & { getResponse: true }): Promise<ConsumerResponse<unknown>>;
  <TData = unknown>(url: string, options?: ConsumerRequestOptions): Promise<TData>;
}

declare module '@umijs/max' {
  export const Link: typeof import('./.umi/exports').Link;
  export const useLocation: typeof import('./.umi/exports').useLocation;
  export const useNavigate: typeof import('./.umi/exports').useNavigate;
  export const request: ConsumerRequest;
}
