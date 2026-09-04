import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';
import MerchantsPage from './merchants';
import { getMerchant, getMerchants } from '../services/ops';

vi.mock('../components/AuthGate', () => ({
  AuthGate: ({ children }: { children: ReactNode }) => children
}));

vi.mock('../services/auth', () => ({
  getSession: vi.fn().mockResolvedValue({
    authenticated: true,
    admin: { permissions: [
      'ops.merchant.read', 'ops.merchant.write', 'ops.application.read'
    ] }
  })
}));

vi.mock('../services/ops', () => ({
  getMerchants: vi.fn(),
  getMerchant: vi.fn(),
  createMerchant: vi.fn(),
  updateMerchant: vi.fn(),
  changeMerchantStatus: vi.fn(),
  freezeMerchant: vi.fn(),
  unfreezeMerchant: vi.fn(),
  deleteMerchant: vi.fn()
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <ConfigProvider locale={zhCN}>
      <App>
        <QueryClientProvider client={queryClient}><MerchantsPage /></QueryClientProvider>
      </App>
    </ConfigProvider>
  );
}

describe('merchant management page', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', '/merchants');
    vi.mocked(getMerchants).mockResolvedValue({ items: [], page: 0, size: 20, total: 0 });
  });

  it('supports independent merchant number/name filters and renders Chinese pagination', async () => {
    const user = userEvent.setup();
    renderPage();
    await waitFor(() => expect(getMerchants).toHaveBeenCalled());

    await user.type(screen.getByPlaceholderText('输入商户号'), 'M2026');
    await user.type(screen.getByPlaceholderText('输入商户名称'), '星河');
    await user.click(screen.getByRole('button', { name: /查\s*询/ }));

    await waitFor(() => expect(getMerchants).toHaveBeenLastCalledWith({
      page: 0, size: 20, merchantNo: 'M2026', name: '星河'
    }));
    expect(screen.queryByText(/Page/)).toBeNull();
    expect(screen.getByRole('table').style.width).not.toBe('1500px');
  });

  it('uses a complete merchant profile drawer and links to filtered application management', async () => {
    vi.mocked(getMerchants).mockResolvedValue({
      page: 0, size: 20, total: 1,
      items: [{
        merchantId: 'merchant-1', merchantNo: 'M2026', name: '星河便利店',
        shortName: '星河便利', contactName: '张三', contactMobile: '13800000001',
        merchantType: 'PERSONAL', accountLinked: true,
        profileComplete: true, status: 'ACTIVE', applicationCount: 0, deletable: true,
        deletionBlockedReason: null, createdAt: '2026-08-03T00:00:00Z',
        updatedAt: '2026-08-03T00:00:00Z', version: 0
      }]
    });
    const user = userEvent.setup();
    renderPage();
    await waitFor(() => expect(screen.getByRole('button', { name: '新建商户' })).toBeTruthy());

    await user.click(screen.getByRole('button', { name: '新建商户' }));

    expect(screen.getByRole('dialog')).toBeTruthy();
    expect(screen.getByLabelText('商户名称')).toBeTruthy();
    expect(screen.getByLabelText('商户简称')).toBeTruthy();
    expect(screen.getByLabelText('商户类型')).toBeTruthy();
    expect(screen.getByLabelText('经营类目编码（MCC）')).toBeTruthy();
    expect(screen.getByLabelText('联系人姓名')).toBeTruthy();
    expect(screen.getByLabelText('联系人手机号').getAttribute('maxlength')).toBe('11');
    expect(screen.getByLabelText('联系人邮箱')).toBeTruthy();
    expect(screen.getByLabelText('经营地址')).toBeTruthy();
    expect(screen.getByText('经营位置（地图选点）')).toBeTruthy();
    expect(screen.getByText('地图服务未加载（未配置 AMAP_KEY），可直接填写文字地址。')).toBeTruthy();
    expect(screen.getByText('店铺图片')).toBeTruthy();
    expect(screen.getByRole('button', { name: /上\s*传/ })).toBeTruthy();
    expect(screen.getByLabelText('备注')).toBeTruthy();
    const applicationButton = screen.getByRole('button', { name: '应用配置' });
    expect(applicationButton.hasAttribute('disabled')).toBe(false);
    await user.click(applicationButton);
    expect(window.location.pathname).toBe('/applications');
    expect(window.location.search).toBe('?merchantId=merchant-1');
  });

  it('opens the merchant detail drawer from the application page link and clears the url on close', async () => {
    window.history.replaceState({}, '', '/merchants?merchantId=merchant-1');
    vi.mocked(getMerchants).mockResolvedValue({
      page: 0, size: 20, total: 1,
      items: [{
        merchantId: 'merchant-1', merchantNo: 'M2026', name: '星河便利店',
        shortName: '星河便利', contactName: '张三', contactMobile: '13800000001',
        merchantType: 'PERSONAL', accountLinked: true,
        profileComplete: true, status: 'ACTIVE', applicationCount: 0, deletable: true,
        deletionBlockedReason: null, createdAt: '2026-08-03T00:00:00Z',
        updatedAt: '2026-08-03T00:00:00Z', version: 0
      }]
    });
    vi.mocked(getMerchant).mockResolvedValue({
      merchantId: 'merchant-1', merchantNo: 'M2026', name: '星河便利店',
      shortName: '星河便利', contactName: '张三', contactMobile: '13800000001',
      merchantType: 'PERSONAL', accountLinked: true,
      profileComplete: true, status: 'ACTIVE', applicationCount: 0, deletable: true,
      deletionBlockedReason: null, createdAt: '2026-08-03T00:00:00Z',
      updatedAt: '2026-08-03T00:00:00Z', version: 0
    });
    const user = userEvent.setup();
    renderPage();
    await waitFor(() => expect(getMerchant).toHaveBeenCalledWith('merchant-1'));
    expect(screen.getByText('商户详情')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: '关闭' }));
    await waitFor(() => expect(window.location.search).toBe(''));
  });
});
