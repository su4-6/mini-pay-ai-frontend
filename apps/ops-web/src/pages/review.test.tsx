import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';
import ReviewPage from './review';
import { getMerchantApplies } from '../services/ops';

vi.mock('../components/AuthGate', () => ({
  AuthGate: ({ children }: { children: ReactNode }) => children
}));

vi.mock('../services/auth', () => ({
  getSession: vi.fn().mockResolvedValue({
    authenticated: true,
    admin: { permissions: ['ops.merchant.read', 'ops.merchant.write'] }
  })
}));

vi.mock('../services/ops', () => ({
  getMerchantApplies: vi.fn(),
  getMerchantApply: vi.fn(),
  approveMerchantApply: vi.fn(),
  rejectMerchantApply: vi.fn(),
  requestSupplementMerchantApply: vi.fn()
}));

const pendingApply = {
  id: 1,
  userId: '019fb3d0-2000-7000-8000-000000000001',
  merchantType: 'PERSONAL' as const,
  shopName: '星河小卖部',
  mccCode: '5811',
  address: '上海市浦东新区',
  shopImages: null,
  contactName: '张三',
  contactMobile: '13800000001',
  contactEmail: null,
  remark: null,
  applyStatus: 'PENDING' as const,
  rejectReason: null,
  auditAdminId: null,
  resultantMerchantId: null,
  applyTime: '2026-08-04T02:00:00Z',
  auditTime: null,
  version: 0,
  createdAt: '2026-08-04T02:00:00Z',
  updatedAt: '2026-08-04T02:00:00Z'
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <ConfigProvider locale={zhCN}>
      <App>
        <QueryClientProvider client={queryClient}><ReviewPage /></QueryClientProvider>
      </App>
    </ConfigProvider>
  );
}

describe('merchant review page', () => {
  beforeEach(() => {
    vi.mocked(getMerchantApplies).mockResolvedValue({
      items: [], page: 0, size: 20, total: 0
    });
  });

  it('lists pending applies and opens the review modal', async () => {
    vi.mocked(getMerchantApplies).mockResolvedValue({
      page: 0, size: 20, total: 1, items: [pendingApply]
    });
    const user = userEvent.setup();
    renderPage();
    await waitFor(() => expect(screen.getByText('星河小卖部')).toBeTruthy());

    expect(screen.getByText('13800000001')).toBeTruthy();
    expect(screen.getAllByText('待审核').length).toBeGreaterThan(0);

    await user.click(screen.getByRole('button', { name: /^审\s*核$/ }));
    expect(screen.getByText('审核入驻申请')).toBeTruthy();
    expect(screen.getByRole('radio', { name: '审核通过' })).toBeTruthy();
    expect(screen.getByRole('radio', { name: '驳回' })).toBeTruthy();
    expect(screen.getByRole('radio', { name: '要求补充资料' })).toBeTruthy();
  });

  it('filters by status tab', async () => {
    const user = userEvent.setup();
    renderPage();
    await waitFor(() => expect(getMerchantApplies).toHaveBeenCalled());

    await user.click(screen.getByRole('button', { name: '已通过' }));
    await waitFor(() => expect(getMerchantApplies).toHaveBeenLastCalledWith({
      page: 0, size: 20, applyStatus: 'APPROVED'
    }));

    await user.click(screen.getByRole('button', { name: /全\s*部/ }));
    await waitFor(() => expect(getMerchantApplies).toHaveBeenLastCalledWith({
      page: 0, size: 20
    }));
  });
});
