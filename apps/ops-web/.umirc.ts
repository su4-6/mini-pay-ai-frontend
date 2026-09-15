import { defineConfig } from '@umijs/max';

// Keep local development at the site root, but make production builds safe to
// deploy below /ops/. Without this prefix the generated HTML requests hashed
// bundles from `/`, where the admin application is mounted, and the page stays
// blank when those files inevitably return 404.
const deployBase = process.env.MINIPAY_DEPLOY_BASE
  || (process.env.NODE_ENV === 'production' ? '/ops/' : '/');
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
  cssMinifierOptions: { charset: 'utf8' },
  antd: {},
  define: {
    MINIPAY_PUBLIC_PATH: deployBase,
    // 三个门户地址只在显式传入时才注入。
    // 之前写成 `process.env.X || 'http://localhost:800N/'`，构建流水线并不传这三个变量，
    // 于是 localhost 被烤进产物：部署后跳转会指向构建机的 localhost（ERR_CONNECTION_REFUSED）。
    // 不注入时，源码里的 `typeof X === 'string' ? X : '<相对路径>'` 回退会生效，
    // AuthGate 则按当前域名推导，因此产物不再依赖构建机环境。
    ...(process.env.MERCHANT_WEB_PUBLIC_URL
      ? { MERCHANT_WEB_PUBLIC_URL: process.env.MERCHANT_WEB_PUBLIC_URL }
      : {}),
    ...(process.env.OPS_WEB_PUBLIC_URL
      ? { OPS_WEB_PUBLIC_URL: process.env.OPS_WEB_PUBLIC_URL }
      : {}),
    ...(process.env.ADMIN_WEB_PUBLIC_URL
      ? { ADMIN_WEB_PUBLIC_URL: process.env.ADMIN_WEB_PUBLIC_URL }
      : {}),
    AMAP_WEB_KEY: process.env.AMAP_KEY || '',
    AMAP_SECURITY_CODE: process.env.AMAP_SECURITY_CODE || '',
    AMAP_SERVICE_HOST: process.env.AMAP_SERVICE_HOST || ''
  },
  title: 'MiniPay 运营平台',
  favicons: [`${deployBase}minipay-logo.jpg`],
  proxy: {
    // 登录接口属于身份服务，使用独立前缀避免和 Management BFF 的 /api/v1/csrf 混淆。
    '/identity': {
      target: 'http://localhost:8081',
      changeOrigin: true,
      pathRewrite: { '^/identity': '' }
    },
    '/api': { target: 'http://localhost:8088', changeOrigin: true },
    '/oauth2': { target: 'http://localhost:8088', changeOrigin: true },
    '/login/oauth2': { target: 'http://localhost:8088', changeOrigin: true },
    '/switch-login': { target: 'http://localhost:8088', changeOrigin: true },
    '/logout': { target: 'http://localhost:8088', changeOrigin: true }
  },
  routes: [
    { path: '/', component: 'index' },
    { path: '/login', component: 'login' },
    { path: '/merchants', component: 'merchants' },
    { path: '/review', component: 'review' },
    { path: '/application-review', component: 'application-review' },
    { path: '/applications', component: 'applications' },
    { path: '/payments', component: 'payments' },
    { path: '/refunds', component: 'refunds' },
    { path: '/transfers', component: 'transfers' },
    { path: '/recharges', component: 'recharges' },
    { path: '/withdrawals', component: 'withdrawals' },
    { path: '/food-orders', component: 'food-orders' },
    { path: '/collection-records', component: 'collection-records' },
    { path: '/notifications', component: 'notifications' },
    { path: '/system/audits/login', component: 'login-audits' },
    { path: '/login-audits', redirect: '/system/audits/login' }
  ]
});
