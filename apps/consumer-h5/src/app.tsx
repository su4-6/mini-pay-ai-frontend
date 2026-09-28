import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ApiProblemError } from '@minipay/api-client';
import dayjs from 'dayjs';
import type { ReactNode } from 'react';
import { AppErrorBoundary } from './components/AppErrorBoundary';
import { onUnauthorized } from './services/http';
import { queryKeys } from './query/keys';
import { installChunkRecovery } from './utils/chunk-recovery';
import 'dayjs/locale/zh-cn';
import 'antd-mobile/es/global';
import './global.less';

dayjs.locale('zh-cn');
installChunkRecovery();

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

/**
 * ⚠️ 不要 `export` 这个实例！Umi 会把 `src/app.tsx` 的每个导出都当作运行时插件注册
 * （见生成的 `.umi/core/plugin.ts` 里 `validKeys` 白名单），而白名单里没有 `queryClient`
 * —— 2026-09-27 实测：导出它会让 `pluginManager.register()` 的断言直接抛
 * `register failed, invalid key queryClient .`，整应用启动中断、页面白屏。
 * 页面里要拿客户端请用 `useQueryClient()`；这里保持模块私有。
 */
const queryClient = new QueryClient({
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
  return (
    <AppErrorBoundary>
      <QueryClientProvider client={queryClient}>{container}</QueryClientProvider>
    </AppErrorBoundary>
  );
}

/**
 * Umi Request 全局配置（统一请求层，禁止再引入第二套 axios 层）。
 * 业务调用统一经过 `src/services/http.ts`，在那里补 CSRF 头并映射 Problem Details。
 *
 * ⚠️ 不要在这里写 `requestInterceptors`（2026-09-27 实测踩坑，表现为整个 H5 打不开）：
 * 生成的 `.umi/plugin-request/request.ts` 底层是 **axios**，按 axios 拦截器签名调用；
 * 而 umi-request 那种「返回 `[url, options]` 元组」的写法会被解构成
 * `{ url: newUrl, options }` 再 `{ ...options, url }` —— 配置里**丢掉了 method**，
 * axios 随即在 `config.method.toUpperCase()` 抛
 * `Cannot read properties of undefined (reading 'toUpperCase')`，
 * 被 `services/http.ts` 归一化成「网络不可用」，于是所有 GET（含会话查询）全挂。
 *
 * `X-Request-Id` / `Accept` / `withCredentials` 已在 transport 里逐请求设置，
 * 这里只保留超时这类全局默认值。
 */
export const request = {
  timeout: 15_000,
  withCredentials: true
};
