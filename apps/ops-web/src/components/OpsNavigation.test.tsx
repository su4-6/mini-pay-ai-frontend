import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { opsRoutes } from '../config/navigation';
import { OpsNavigation } from './OpsNavigation';

describe('OpsNavigation', () => {
  it('renders the approved groups and navigates the implemented pages', async () => {
    const onNavigate = vi.fn();
    const user = userEvent.setup();
    render(
      <OpsNavigation routes={opsRoutes} activeKey="merchants" onNavigate={onNavigate} />
    );

    expect(screen.getByText('主页')).toBeTruthy();
    expect(screen.getByText('商户管理')).toBeTruthy();
    expect(screen.getByText('商户列表')).toBeTruthy();
    expect(screen.getByText('应用列表')).toBeTruthy();
    expect(screen.queryByText('待开发')).toBeNull();
    expect(screen.getByText('支付订单')).toBeTruthy();
    expect(screen.getByText('退款订单')).toBeTruthy();
    expect(screen.getByText('转账订单')).toBeTruthy();
    expect(screen.getByText('商户通知')).toBeTruthy();
    expect(screen.getByRole('menuitem', { name: /应用列表/ }).getAttribute('aria-disabled'))
      .not.toBe('true');

    await user.click(screen.getByText('应用列表'));
    expect(onNavigate).toHaveBeenCalledWith(
      expect.objectContaining({ key: 'applications', path: '/applications' })
    );
    await user.click(screen.getByText('商户列表'));
    expect(onNavigate).toHaveBeenCalledWith(
      expect.objectContaining({ key: 'merchants', path: '/merchants' })
    );
    await user.click(screen.getByText('支付订单'));
    expect(onNavigate).toHaveBeenCalledWith(
      expect.objectContaining({ key: 'payments', path: '/payments' })
    );

    ['通道管理', '账务中心', '结算中心', '系统管理', '沙箱管理', '登录审计']
      .forEach((name) => expect(screen.queryByText(name)).toBeNull());
  });

  it('keeps only the homepage and two approved groups when collapsed', () => {
    const { container } = render(
      <OpsNavigation routes={opsRoutes} activeKey="dashboard" collapsed onNavigate={vi.fn()} />
    );

    expect(container.querySelector('.ant-menu-inline-collapsed')).not.toBeNull();
    expect(container.querySelectorAll('.anticon')).toHaveLength(0);
    expect(container.querySelectorAll('[class*="railMark"]')).toHaveLength(3);
    ['主页', '商户管理', '订单管理'].forEach((name) => {
      expect(screen.getByRole('menuitem', { name })).toBeTruthy();
    });
  });
});
