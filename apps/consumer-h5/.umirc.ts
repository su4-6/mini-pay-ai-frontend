import { defineConfig } from '@umijs/max';

/**
 * 线上 C 端部署在独立主机 `https://app.su46proj.site/` 的根路径，
 * 因此 base/publicPath 固定为 `/`（不是子路径）。留出 MINIPAY_DEPLOY_BASE
 * 仅用于本地/预发在子路径下验证，生产不传该变量。
 */
const deployBase = process.env.MINIPAY_DEPLOY_BASE || '/';

/** 本地开发时 `/api/**` 代理到 consumer-bff；生产由网关路由，前端不感知。 */
const bffProxyTarget = process.env.CONSUMER_BFF_PROXY_TARGET || 'http://localhost:8087';

export default defineConfig({
  hash: true,
  base: deployBase,
  publicPath: deployBase,
  npmClient: 'pnpm',
  mfsu: false,
  request: {},
  esbuildMinifyIIFE: true,
  jsMinifier: 'terser',
  codeSplitting: { jsStrategy: 'granularChunks' },
  // 路由组件是异步分包。用户看到首页后立即预取页面中的导航目标，
  // 避免移动网络下第一次点进功能页才开始下载、误以为页面白屏。
  routePrefetch: { defaultPrefetch: 'render', defaultPrefetchTimeout: 100 },
  cssMinifierOptions: { charset: 'utf8' },
  define: {
    MINIPAY_PUBLIC_PATH: deployBase,
    CONSUMER_BFF_PUBLIC_PATH: process.env.CONSUMER_BFF_PUBLIC_PATH || '',
    AMAP_WEB_KEY: process.env.AMAP_KEY || '',
    AMAP_SECURITY_CODE: process.env.AMAP_SECURITY_CODE || '',
    AMAP_SERVICE_HOST: process.env.AMAP_SERVICE_HOST || ''
  },
  title: 'MiniPay 钱包',
  favicons: [`${deployBase}minipay-logo.jpg`],
  proxy: {
    '/api': { target: bffProxyTarget, changeOrigin: true }
  },
  routes: [
    { path: '/', component: 'home' },
    { path: '/miling', component: 'chat' },
    { path: '/login', component: 'login' },
    { path: '/onboarding', component: 'onboarding' },
    { path: '/wallet', component: 'wallet' },
    { path: '/bills', component: 'bills' },
    { path: '/collect', component: 'collect' },
    { path: '/merchant', component: 'merchant' },
    { path: '/transfer', component: 'transfer' },
    { path: '/transfer/result', component: 'transfer-result' },
    { path: '/pay', component: 'pay' },
    { path: '/transfers', component: 'transfers' },
    { path: '/transfers/detail', component: 'transfer-detail' },
    { path: '/pay-password', component: 'pay-password' },
    { path: '/real-name', component: 'real-name' },
    { path: '/bank-cards', component: 'bank-cards' },
    { path: '/funding', component: 'funding' },
    { path: '/profile', component: 'profile' },
    { path: '/security', component: 'security' },
    { path: '/legal/service', component: 'legal-service' },
    { path: '/legal/privacy', component: 'legal-privacy' },
    { path: '/me', component: 'me' },
    { path: '*', redirect: '/' }
  ]
});
