import { defineConfig } from '@umijs/max';
const deployBase = process.env.MINIPAY_DEPLOY_BASE || '/';
export default defineConfig({
  hash: true,
  base: deployBase,
  publicPath: deployBase,
  npmClient: 'pnpm',
  mfsu: false,
  request: {},
  antd: {},
  define: {
    MINIPAY_PUBLIC_PATH: deployBase,
    MERCHANT_WEB_PUBLIC_URL: process.env.MERCHANT_WEB_PUBLIC_URL || 'http://localhost:8001/',
    OPS_WEB_PUBLIC_URL: process.env.OPS_WEB_PUBLIC_URL || 'http://localhost:8000/',
    ADMIN_WEB_PUBLIC_URL: process.env.ADMIN_WEB_PUBLIC_URL || 'http://localhost:8002/'
  },
  esbuildMinifyIIFE: true,
  codeSplitting: { jsStrategy: 'granularChunks' },
  jsMinifierOptions: { charset: 'utf8' },
  cssMinifierOptions: { charset: 'utf8' },
  title: 'MiniPay 系统管理平台',
  favicons: [`${deployBase}minipay-logo.svg`],
  proxy: {
    '/identity': {
      target: 'http://localhost:8081',
      changeOrigin: true,
      pathRewrite: { '^/identity': '' }
    },
    '/api': { target: 'http://localhost:8089', changeOrigin: true },
    '/oauth2': { target: 'http://localhost:8089', changeOrigin: true },
    '/login/oauth2': { target: 'http://localhost:8089', changeOrigin: true },
    '/switch-login': { target: 'http://localhost:8089', changeOrigin: true },
    '/logout': { target: 'http://localhost:8089', changeOrigin: true }
  },
  routes: [
    { path: '/', component: 'index' },
    { path: '/login', component: 'login' },
    { path: '/accounts', component: 'index' },
    { path: '/merchants', component: 'index' },
    { path: '/orders', component: 'index' },
    { path: '/wallets', component: 'index' },
    { path: '/backoffice', component: 'index' },
    { path: '/audits', component: 'index' },
    { path: '/security', component: 'index' },
    { path: '/settings', component: 'index' }
  ]
});
