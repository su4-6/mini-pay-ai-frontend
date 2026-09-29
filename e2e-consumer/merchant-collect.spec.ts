import { expect, test } from '@playwright/test';

const personalCode = 'minipay://collect/personal?token=personal-demo-token';
const merchantCode = 'minipay://collect/merchant?token=merchant-demo-token';

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (path === '/api/v1/session') return json({
      authenticated: true,
      userId: '019fb3d0-0000-7000-8000-000000000001',
      phone: '139****0000',
      displayName: '苏先生',
      payPasswordSet: true,
      onboardingRequired: false,
      realNameStatus: 'VERIFIED',
      realNameVerified: true
    });
    if (path === '/api/v1/csrf') return json({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'test' });
    if (path === '/api/v1/collection-code') return json({ deepLink: personalCode, expiresAt: '2036-09-29T12:00:00Z', sandboxNotice: '沙箱演示收款码' });
    if (path === '/api/v1/merchant-center/merchants') return json([{ merchantId: '019fb3d0-0000-7000-8000-000000000002', merchantNo: 'M202609290001', name: '米粒便利店', status: 'ACTIVE', initialized: true }]);
    if (path === '/api/v1/merchant-center/onboardings') return json({ items: [{ id: 3, shopName: '米粒便利店', applyStatus: 'APPROVED', resultantMerchantId: '019fb3d0-0000-7000-8000-000000000002', version: 1, updatedAt: '2026-09-29T03:00:00Z' }], page: 0, size: 20, total: 1 });
    if (path === '/api/v1/merchant-center/collection-code') return json({ merchant: { merchantId: '019fb3d0-0000-7000-8000-000000000002', name: '米粒便利店', status: 'ACTIVE' }, collectionCode: { status: 'ENABLED', qrContent: merchantCode } });
    return json({ code: 'UNMOCKED', title: path }, 404);
  });
});

test('个人与商户收款码可切换且不显示原始长令牌', async ({ page }) => {
  await page.goto('/collect');
  await expect(page.getByText('个人收款码', { exact: true })).toBeVisible();
  await expect(page.getByText('个人转账与商户收款，共用同一个钱包')).toBeVisible();
  await expect(page.getByText(personalCode)).toHaveCount(0);
  await page.getByRole('tab', { name: '商户收款' }).click();
  await expect(page.getByText('米粒便利店')).toBeVisible();
  await expect(page.getByText('对方扫码后进入商户付款确认')).toBeVisible();
  await expect(page.getByText(merchantCode)).toHaveCount(0);
  await page.screenshot({ path: 'output/playwright/consumer-collect-mobile.png', fullPage: true });
});

test('C端商户中心展示与B端同一条申请和共享钱包说明', async ({ page }) => {
  await page.goto('/merchant');
  await expect(page.getByText('一个钱包', { exact: false })).toBeVisible();
  await expect(page.getByText('米粒便利店').first()).toBeVisible();
  await expect(page.getByText('已通过')).toBeVisible();
  await expect(page.getByText('这就是商户 B 端看到的同一条申请记录，无需再次申请。')).toBeVisible();
  await page.screenshot({ path: 'output/playwright/consumer-merchant-mobile.png', fullPage: true });
});
