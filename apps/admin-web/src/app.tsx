import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import type { ReactNode } from 'react';
import { minipayDesktopTheme } from '@minipay/ui-desktop';
const client = new QueryClient({ defaultOptions: { queries: { retry: 1, staleTime: 15_000 } } });
export function rootContainer(container: ReactNode) {
  return (
    <ConfigProvider locale={zhCN} theme={minipayDesktopTheme}>
      <App>
        <QueryClientProvider client={client}>{container}</QueryClientProvider>
      </App>
    </ConfigProvider>
  );
}
