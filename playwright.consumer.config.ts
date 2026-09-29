import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e-consumer',
  expect: { timeout: 30_000 },
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:8003',
    channel: 'msedge',
    headless: true,
    viewport: { width: 390, height: 844 },
    trace: 'retain-on-failure'
  },
  webServer: {
    command: 'pnpm --filter @minipay/consumer-h5 dev',
    url: 'http://127.0.0.1:8003',
    reuseExistingServer: true,
    timeout: 180_000
  }
});
