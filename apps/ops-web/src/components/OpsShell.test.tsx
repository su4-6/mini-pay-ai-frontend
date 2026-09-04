import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from 'antd';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';
import { OpsShell } from './OpsShell';

const admin = {
  userId: 'admin-1',
  displayName: '演示管理员',
  roles: ['platform_admin'],
  permissions: [
    'ops.portal', 'ops.audit.read', 'ops.dashboard.read',
    'ops.merchant.read', 'ops.merchant.write'
  ]
};

function renderShell(activeKey: 'dashboard' | 'merchants' | 'login-audits', child: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  });
  const view = render(
    <QueryClientProvider client={queryClient}>
      <App>
        <OpsShell admin={admin} activeKey={activeKey}>{child}</OpsShell>
      </App>
    </QueryClientProvider>
  );
  return { ...view, queryClient };
}

describe('OpsShell', () => {
  it('renders the light shell, homepage-first breadcrumbs, and desktop preference', async () => {
    const user = userEvent.setup();
    window.localStorage.setItem('minipay:ops:sidebar-collapsed', 'false');
    const { container } = renderShell('merchants', <div>商户内容</div>);

    expect(screen.getByText('minipay')).toBeTruthy();
    expect(screen.getByRole('button', { name: '通知功能待开发' }).hasAttribute('disabled'))
      .toBe(true);

    const breadcrumb = container.querySelector<HTMLElement>('.ant-breadcrumb');
    expect(breadcrumb).not.toBeNull();
    expect(within(breadcrumb!).getByText('主页')).toBeTruthy();
    expect(within(breadcrumb!).getByText('商户管理')).toBeTruthy();
    expect(within(breadcrumb!).getByText('商户列表')).toBeTruthy();

    const toggle = screen.getByRole('button', { name: '收起侧栏' });
    await user.click(toggle);
    expect(screen.getByRole('button', { name: '展开侧栏' })).toBeTruthy();
    expect(window.localStorage.getItem('minipay:ops:sidebar-collapsed')).toBe('true');
  });

  it('refreshes active business queries without invalidating the session', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderShell('dashboard', <div>主页内容</div>);
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');

    await user.click(screen.getByRole('button', { name: '刷新当前页面数据' }));
    await waitFor(() => expect(invalidate).toHaveBeenCalled());

    const options = invalidate.mock.calls[0][0];
    expect(options?.refetchType).toBe('active');
    expect(options?.predicate?.({ queryKey: ['session'] } as never)).toBe(false);
    expect(options?.predicate?.({ queryKey: ['ops-dashboard', '7d'] } as never)).toBe(true);
  });

  it('keeps the hidden login audit direct page protected and breadcrumbed', () => {
    const { container } = renderShell('login-audits', <div>审计内容</div>);
    const breadcrumb = container.querySelector<HTMLElement>('.ant-breadcrumb');

    expect(within(breadcrumb!).getByText('安全审计')).toBeTruthy();
    expect(within(breadcrumb!).getByText('登录审计')).toBeTruthy();
    expect(screen.queryByRole('menuitem', { name: '登录审计' })).toBeNull();
  });

  it('closes the mobile drawer with Escape and restores trigger focus', async () => {
    const user = userEvent.setup();
    renderShell('dashboard', <div>主页内容</div>);

    const trigger = screen.getByRole('button', { name: '打开主导航' });
    await user.click(trigger);
    expect(screen.getByRole('button', { name: '关闭主导航' })).toBeTruthy();
    expect(screen.getByText('运营平台导航')).toBeTruthy();

    await user.keyboard('{Escape}');
    await waitFor(() => {
      expect(screen.getByRole('button', { name: '打开主导航' })).toBe(document.activeElement);
    });
  });
});
