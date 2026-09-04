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

async function mockSession(page: Page) {
  await page.route('**/api/v1/session', (route) => route.fulfill({ json: adminSession }));
}

test('renders the operations platform dashboard rather than a merchant dashboard', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await mockSession(page);
  await page.route('**/api/v1/ops/dashboard?range=*', (route) => route.fulfill({
    json: {
      range: '7d', timezone: 'Asia/Shanghai', from: '2026-07-28', to: '2026-08-03',
      dataAsOf: '2026-08-03T03:00:00Z',
      summary: {
        paymentAmountCent: 1234567, paymentCount: 190,
        successRateBasisPoints: 9500, refundAmountCent: 23500, activeMerchantCount: 8
      },
      trend: Array.from({ length: 7 }, (_, index) => ({
        date: `2026-0${index < 4 ? '7' : '8'}-${String(index < 4 ? 28 + index : index - 3).padStart(2, '0')}`,
        submittedPaymentCount: 20, successfulPaymentCount: 19,
        paymentAmountCent: 100000 + index * 1000,
        successfulRefundCount: 1, refundAmountCent: 2000
      })),
      pending: {
        abnormalPaymentCount: 1, abnormalRefundCount: 2,
        abnormalTransferCount: 0, failedNotificationCount: 3
      }
    }
  }));

  await page.goto('/');

  await expect(page.getByRole('heading', { name: '运营平台总览' })).toBeVisible();
  await expect(page.getByText('平台交易金额')).toBeVisible();
  await expect(page.getByText('活跃商户', { exact: true })).toBeVisible();
  await expect(page.getByText('商户主页')).toHaveCount(0);

  const navigation = page.getByRole('navigation', { name: '运营平台主导航' });
  await expect(navigation.getByText('主页')).toBeVisible();
  await expect(navigation.getByText('商户管理')).toBeVisible();
  await expect(navigation.getByText('订单管理')).toBeVisible();
  await expect(navigation.getByText('应用列表')).toBeVisible();
  await expect(navigation.getByText('支付订单')).toBeVisible();
  await expect(navigation.getByText('退款订单')).toBeVisible();
  await expect(navigation.getByText('转账订单')).toBeVisible();
  await expect(navigation.getByText('商户通知')).toBeVisible();
  await expect(navigation.getByText('待开发')).toHaveCount(4);
  await expect(navigation.getByRole('menuitem', { name: /应用列表/ })).toBeEnabled();
  await expect(navigation.getByText('通道管理')).toHaveCount(0);
  await expect(navigation.getByText('账务中心')).toHaveCount(0);
  await expect(navigation.getByText('系统管理')).toHaveCount(0);
  await expect(navigation.getByText('沙箱管理')).toHaveCount(0);

  const desktopSidebar = page.locator('aside');
  await expect(desktopSidebar).toHaveCSS('width', '188px');
  await expect(desktopSidebar).toHaveCSS('background-color', 'rgb(255, 255, 255)');

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(desktopSidebar).toBeHidden();
  await page.getByRole('button', { name: '打开主导航' }).click();
  const mobileDrawer = page.getByRole('dialog', { name: '运营平台导航' });
  await expect(mobileDrawer).toBeVisible();
  await expect(mobileDrawer.getByText('minipay')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('button', { name: '打开主导航' })).toBeFocused();
});

test('creates a merchant through mocked BFF responses without test authentication backdoors', async ({ page }) => {
  await mockSession(page);
  const merchants = [{
    merchantId: '019fb3d0-1000-7000-8000-000000000001',
    merchantNo: 'M202608030001', name: '星河便利店', shortName: '星河便利',
    contactName: '张三', contactMobile: '13800000001', contactEmail: null,
    remark: null, profileComplete: true, status: 'ACTIVE',
    applicationCount: 0, deletable: true, deletionBlockedReason: null,
    createdAt: '2026-08-03T00:00:00Z', updatedAt: '2026-08-03T00:00:00Z', version: 0
  }];
  await page.route('**/api/v1/csrf', (route) => route.fulfill({
    json: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'e2e-csrf' }
  }));
  await page.route('**/api/v1/ops/merchants?*', (route) => route.fulfill({
    json: { items: merchants, page: 0, size: 20, total: merchants.length }
  }));
  await page.route('**/api/v1/ops/merchants', async (route) => {
    if (route.request().method() !== 'POST') return route.fallback();
    const body = route.request().postDataJSON() as {
      name: string; shortName: string; contactName: string; contactMobile: string
    };
    const created = {
      ...merchants[0], merchantId: '019fb3d0-1000-7000-8000-000000000002',
      merchantNo: 'M202608030002', ...body, profileComplete: true
    };
    merchants.push(created);
    await route.fulfill({ status: 201, json: created });
  });

  await page.goto('/merchants');
  await expect(page.getByRole('heading', { name: '商户管理' })).toBeVisible();
  await page.getByRole('button', { name: '新建商户' }).click();
  await page.getByPlaceholder('请输入商户注册名称').fill('云帆咖啡');
  await page.getByPlaceholder('请输入对外展示简称').fill('云帆咖啡');
  await page.getByPlaceholder('请输入联系人姓名').fill('李四');
  await page.getByPlaceholder('请输入 11 位手机号').fill('13900000001');
  await page.getByRole('dialog', { name: '新建商户' })
    .getByRole('button', { name: /创\s*建/ }).click();

  await expect(page.getByRole('cell', { name: '云帆咖啡' }).first()).toBeVisible();
  await expect(page.getByText('商户已创建')).toBeVisible();
});

test('creates, disables, and deletes an application through mocked BFF responses', async ({ page }) => {
  await mockSession(page);
  const merchant = {
    merchantId: '019fb3d0-1000-7000-8000-000000000001',
    merchantNo: 'M202608030001', name: '星河便利店', shortName: '星河便利',
    contactName: '张三', contactMobile: '13800000001', profileComplete: true,
    status: 'ACTIVE', applicationCount: 0, deletable: true, deletionBlockedReason: null,
    createdAt: '2026-08-03T00:00:00Z', updatedAt: '2026-08-03T00:00:00Z', version: 0
  };
  const applications: Array<Record<string, unknown>> = [];
  await page.route('**/api/v1/csrf', (route) => route.fulfill({
    json: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'e2e-csrf' }
  }));
  await page.route('**/api/v1/ops/merchants?*', (route) => route.fulfill({
    json: { items: [merchant], page: 0, size: 20, total: 1 }
  }));
  await page.route('**/api/v1/ops/applications?*', (route) => route.fulfill({
    json: { items: applications, page: 0, size: 20, total: applications.length }
  }));
  await page.route('**/api/v1/ops/applications', async (route) => {
    if (route.request().method() !== 'POST') return route.fallback();
    const body = route.request().postDataJSON() as {
      merchantId: string; name: string; status: 'ACTIVE' | 'DISABLED'
    };
    const created = {
      applicationId: '019fb3d0-1100-7000-8000-000000000001',
      appId: 'mp_app_019fb3d0110070008000000000000001',
      ...body,
      merchantNo: merchant.merchantNo,
      merchantName: merchant.name,
      merchantStatus: merchant.status,
      hasTransactions: false,
      deletable: false,
      deletionBlockedReason: 'APPLICATION_MUST_BE_DISABLED',
      createdAt: '2026-08-04T00:00:00Z',
      updatedAt: '2026-08-04T00:00:00Z',
      version: 0
    };
    applications.push(created);
    await route.fulfill({ status: 201, json: created });
  });
  await page.route('**/api/v1/ops/applications/*/disable', async (route) => {
    const application = applications[0];
    Object.assign(application, {
      status: 'DISABLED', deletable: true, deletionBlockedReason: null, version: 1
    });
    await route.fulfill({ json: application });
  });
  await page.route('**/api/v1/ops/applications/*', async (route) => {
    if (route.request().method() === 'DELETE') {
      applications.splice(0, applications.length);
      return route.fulfill({ status: 204, body: '' });
    }
    return route.fallback();
  });

  await page.goto('/applications');
  await expect(page.getByRole('heading', { name: '应用管理' })).toBeVisible();
  await page.getByRole('button', { name: '新建应用' }).click();
  const createDrawer = page.getByRole('dialog', { name: '新建应用' });
  await createDrawer.getByLabel('所属商户').click();
  await page.getByText('星河便利店（M202608030001）').last().click();
  await createDrawer.getByPlaceholder('请输入应用名称').fill('星河收银台');
  await createDrawer.getByRole('button', { name: /创\s*建/ }).click();

  const row = page.getByRole('row').filter({ hasText: '星河收银台' });
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: '停用' }).click();
  await page.locator('.ant-modal-confirm').filter({ hasText: '确认停用应用？' })
    .getByRole('button', { name: /停\s*用/ }).click();
  await expect(row.getByText('已停用')).toBeVisible();

  await row.getByRole('button', { name: '删除' }).click();
  await page.locator('.ant-modal-confirm').filter({ hasText: '确认删除应用？' })
    .getByRole('button', { name: /删\s*除/ }).click();
  await expect(page.getByText('没有符合条件的应用')).toBeVisible();
});
