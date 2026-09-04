import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import type { ReactNode } from 'react';
import { createRequestId } from '@minipay/shared';
import { minipayDesktopTheme } from '@minipay/ui-desktop';

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: 1, staleTime: 30_000 } } });

export function rootContainer(container: ReactNode) {
  return (
    <ConfigProvider locale={zhCN} theme={minipayDesktopTheme}>
      <AntdApp>
        <QueryClientProvider client={queryClient}>{container}</QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  );
}

export const request = {
  timeout: 10_000,
  requestInterceptors: [
    (url: string, options: RequestInit) => [
      url,
      { ...options, headers: { ...options.headers, 'X-Request-Id': createRequestId() } }
    ]
  ]
};
