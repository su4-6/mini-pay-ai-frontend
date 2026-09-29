import { expect, test } from '@playwright/test';

function json(route: Parameters<Parameters<import('@playwright/test').Page['route']>[1]>[0], body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/v1/session') return json(route, { authenticated: true, userId: '019fb3d0-0000-7000-8000-000000000001', phone: '138****0000', displayName: '苏先生', payPasswordSet: true, onboardingRequired: false, realNameStatus: 'VERIFIED', realNameVerified: true });
    if (path === '/api/v1/merchant-center/merchants') return json(route, []);
    if (path === '/api/v1/merchant-center/onboardings') return json(route, { items: [], page: 0, size: 20, total: 0 });
    if (path === '/api/v1/ai/conversations') return json(route, { items: [{ id: 'conversation-1', title: '转账助手', createdAt: '2026-09-29T07:00:00Z' }] });
    if (path === '/api/v1/ai/conversations/conversation-1/messages') return json(route, { items: [{ id: 'message-1', role: 'ASSISTANT', content: '请点击下方按钮进入人工确认页，填写收款人与金额后完成转账。', cardType: 'agent.missing-slots', cardPayload: JSON.stringify({ taskType: 'transfer', missingSlots: { recipient: '请输入收款人' } }), createdAt: '2026-09-29T07:01:00Z' }] });
    return json(route, { code: 'UNMOCKED', title: path }, 404);
  });
});

test('C端展示与B端一致的完整入驻字段和明显的图片按钮', async ({ page }) => {
  await page.goto('/merchant');
  await expect(page.getByText('个人商户', { exact: true })).toBeVisible();
  await expect(page.getByText('个体工商户', { exact: true })).toBeVisible();
  await expect(page.getByText('企业商户', { exact: true })).toBeVisible();
  await expect(page.getByText('上传照片', { exact: true })).toBeVisible();
  await expect(page.getByText('最多上传 5 张', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: '提交入驻申请' })).toBeVisible();
});

test('米灵历史转账请求显示按钮并可进入转账页', async ({ page }) => {
  await page.goto('/miling');
  await page.getByRole('button', { name: '打开会话列表' }).click();
  await page.getByRole('button', { name: '转账助手', exact: true }).click();
  await expect(page.getByRole('button', { name: '去转账' })).toBeVisible();
  await page.getByRole('button', { name: '去转账' }).click();
  await expect(page).toHaveURL(/\/transfer$/);
});
