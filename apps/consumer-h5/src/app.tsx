import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ApiProblemError } from '@minipay/api-client';
import { createRequestId } from '@minipay/shared';
import dayjs from 'dayjs';
import type { ReactNode } from 'react';
import { onUnauthorized } from './services/http';
import { queryKeys } from './query/keys';
import 'dayjs/locale/zh-cn';
import 'antd-mobile/es/global';
import './global.less';

dayjs.locale('zh-cn');

/** 4xx（除 408/429）不重试；网络类错误最多重试 2 次。 */
function shouldRetry(failureCount: number, error: unknown): boolean {
  if (failureCount >= 2) return false;
  if (error instanceof ApiProblemError) {
    const { status, code } = error.problem;
    if (code === 'NETWORK_UNAVAILABLE' || code === 'REQUEST_TIMEOUT') return true;
    if (status >= 400 && status < 500 && status !== 408 && status !== 429) return false;
  }
  return true;
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: shouldRetry,
      staleTime: 30_000,
      refetchOnWindowFocus: false
    },
    mutations: {
      // 资金类写操作绝不自动重试：重放由用户显式确认，幂等由后端保证。
      retry: false
    }
  }
});

// 会话失效（401）时只精确失效会话查询，交由 AuthGate 引导重新登录。
onUnauthorized(() => {
  void queryClient.invalidateQueries({ queryKey: queryKeys.session });
});

export function rootContainer(container: ReactNode) {
  return <QueryClientProvider client={queryClient}>{container}</QueryClientProvider>;
}

/**
 * Umi Request 全局配置（统一请求层，禁止再引入第二套 axios 层）。
 * 业务调用统一经过 `src/services/http.ts`，在那里补 CSRF 头并映射 Problem Details。
 */
export const request = {
  timeout: 15_000,
  withCredentials: true,
  requestInterceptors: [
    (url: string, options: Record<string, unknown>) => [
      url,
      {
        ...options,
        withCredentials: true,
        headers: {
          Accept: 'application/json',
          'X-Request-Id': createRequestId(),
          ...(options.headers as Record<string, string> | undefined)
        }
      }
    ]
  ]
};
