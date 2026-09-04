import type {} from '@umijs/max';

declare global {
  const MINIPAY_PUBLIC_PATH: string;
  const MERCHANT_WEB_PUBLIC_URL: string;
  const OPS_WEB_PUBLIC_URL: string;
  const ADMIN_WEB_PUBLIC_URL: string;
}

declare module '@umijs/max' {
  export const Link: typeof import('./.umi/exports').Link;
  export const useLocation: typeof import('./.umi/exports').useLocation;
}
