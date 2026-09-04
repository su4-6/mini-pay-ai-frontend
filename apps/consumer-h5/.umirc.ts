import { defineConfig } from '@umijs/max';

export default defineConfig({
  npmClient: 'pnpm',
  request: {},
  esbuildMinifyIIFE: true,
  title: 'MiniPay AI',
  favicons: ['/minipay-logo.jpg'],
  routes: [{ path: '/', component: 'index' }]
});
