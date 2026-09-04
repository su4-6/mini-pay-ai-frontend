import { expect, test, type Page } from '@playwright/test';

const adminSession = {
  authenticated: true,
  loginUrl: '/oauth2/authorization/minipay-ops',
  admin: {
    userId: 'admin-e2e',
    displayName: '自动化管理员',
    roles: ['platform_admin'],
    permissions: [
      'ops.portal', 'ops.dashboard.read', 'ops.merchant.read', 'ops.merchant.write',
      'ops.application.read', 'ops.application.write'
    ]
  }
};

const pendingApply = {
  id: 1,
  userId: '019fb3d0-2000-7000-8000-000000000001',
  merchantId: '019fb3d0-1000-7000-8000-000000000001',
  merchantNo: 'M202608030001',
  merchantName: '星河便利店',
  name: '星河收银台',
  applyStatus: 'PENDING',
  rejectReason: null,
  auditAdminId: null,
  resultantApplicationId: null,
  applyTime: '2026-08-04T02:00:00Z',
  auditTime: null,
  version: 0,
  createdAt: '2026-08-04T02:00:00Z',
  updatedAt: '2026-08-04T02:00:00Z'
};

test('reviews a pending application apply through mocked BFF responses', async ({ page }) => {
  await page.route('**/api/v1/session', (route) => route.fulfill({ json: adminSession }));
  await page.route('**/api/v1/csrf', (route) => route.fulfill({
    json: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'e2e-csrf' }
  }));
  const applies = [{ ...pendingApply }];
  await page.route('**/api/v1/ops/application-applies?*', (route) => route.fulfill({
    json: { items: applies, page: 0, size: 20, total: applies.length }
  }));
  await page.route('**/api/v1/ops/application-applies/1', (route) => route.fulfill({
    json: applies[0]
  }));
  await page.route('**/api/v1/ops/application-applies/1/approve', async (route) => {
    Object.assign(applies[0], {
      applyStatus: 'APPROVED',
      auditAdminId: 'admin-e2e',
      auditTime: '2026-08-05T03:00:00Z',
      resultantApplicationId: '019fb3d0-1100-7000-8000-000000000001',
      version: 1
    });
    await route.fulfill({ json: applies[0] });
  });

  await page.goto('/application-review');
  await expect(page.getByRole('heading', { name: '应用审核' })).toBeVisible();
  await expect(page.getByRole('cell', { name: '星河收银台' })).toBeVisible();
  await expect(page.getByRole('cell', { name: '星河便利店' })).toBeVisible();

  await page.getByRole('button', { name: '审核', exact: true }).click();
  const modal = page.getByRole('dialog', { name: '审核应用申请' });
  await expect(modal.getByText('星河收银台')).toBeVisible();
  await modal.getByRole('button', { name: /提\s*交/ }).click();

  await expect(page.getByText('已通过，应用已创建')).toBeVisible();
  await expect(page.getByRole('cell', { name: '已通过' })).toBeVisible();
});
