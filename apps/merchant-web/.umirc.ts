import { defineConfig } from '@umijs/max';

// The public merchant console is mounted below /merchant/ in production.
// Prefixing every generated asset avoids collisions with the admin console at
// `/` and prevents white screens caused by root-level bundle 404 responses.
const deployBase = process.env.MINIPAY_DEPLOY_BASE
  || (process.env.NODE_ENV === 'production' ? '/merchant/' : '/');
export default defineConfig({
  hash: true,
  base: deployBase,
  publicPath: deployBase,
  npmClient: 'pnpm',
  mfsu: false,
  request: {},
  esbuildMinifyIIFE: true,
  codeSplitting: { jsStrategy: 'granularChunks' },
  jsMinifierOptions: { charset: 'utf8' },
  cssMinifierOptions: { charset: 'utf8' },
  antd: {},
  define: {
    MINIPAY_PUBLIC_PATH: deployBase,
    MERCHANT_WEB_PUBLIC_URL: process.env.MERCHANT_WEB_PUBLIC_URL || 'http://localhost:8001/',
    OPS_WEB_PUBLIC_URL: process.env.OPS_WEB_PUBLIC_URL || 'http://localhost:8000/',
    ADMIN_WEB_PUBLIC_URL: process.env.ADMIN_WEB_PUBLIC_URL || 'http://localhost:8002/',
    AMAP_WEB_KEY: process.env.AMAP_KEY || '',
    AMAP_SECURITY_CODE: process.env.AMAP_SECURITY_CODE || '',
    AMAP_SERVICE_HOST: process.env.AMAP_SERVICE_HOST || ''
  },
  title: 'MiniPay AI 商户平台',
  favicons: [`${deployBase}minipay-logo.jpg`],
  proxy: {
    '/api': { target: 'http://localhost:8088', changeOrigin: true },
    '/switch-login': { target: 'http://localhost:8088', changeOrigin: true }
  },
  routes: [
    { path: '/', component: 'index' },
    { path: '/login', component: 'login' },
    { path: '/dashboard', component: 'portal' },
    { path: '/applications', component: 'portal' },
    { path: '/orders', component: 'portal' },
    { path: '/wallet', component: 'portal' },
    { path: '/profile', component: 'portal' },
    { path: '/onboarding', component: 'portal' }
  ]
});
