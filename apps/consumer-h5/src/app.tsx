import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { createRequestId } from '@minipay/shared';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 30_000 }
  }
});

export function rootContainer(container: ReactNode) {
  return <QueryClientProvider client={queryClient}>{container}</QueryClientProvider>;
}

export const request = {
  timeout: 10_000,
  requestInterceptors: [
    (url: string, options: RequestInit) => [
      url,
      {
        ...options,
        headers: {
          ...options.headers,
          'X-Request-Id': createRequestId()
        }
      }
    ]
  ],
  errorConfig: {
    errorHandler(error: Error) {
      throw error;
    }
  }
};
