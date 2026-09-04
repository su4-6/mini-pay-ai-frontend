import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';
import ApplicationReviewPage from './application-review';
import { getApplicationApplies } from '../services/ops';

vi.mock('../components/AuthGate', () => ({
  AuthGate: ({ children }: { children: ReactNode }) => children
}));

vi.mock('../services/auth', () => ({
  getSession: vi.fn().mockResolvedValue({
    authenticated: true,
    admin: { permissions: ['ops.application.read', 'ops.application.write'] }
  })
}));

vi.mock('../services/ops', () => ({
  getApplicationApplies: vi.fn(),
  getApplicationApply: vi.fn(),
  approveApplicationApply: vi.fn(),
  rejectApplicationApply: vi.fn(),
  requestSupplementApplicationApply: vi.fn()
}));

const pendingApply = {
  id: 1,
  userId: '019fb3d0-2000-7000-8000-000000000001',
  merchantId: '019fb3d0-1000-7000-8000-000000000001',
  merchantNo: 'M202608030001',
  merchantName: '星河便利店',
  name: '星河收银台',
  applyStatus: 'PENDING' as const,
  rejectReason: null,
  auditAdminId: null,
  resultantApplicationId: null,
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
        <QueryClientProvider client={queryClient}><ApplicationReviewPage /></QueryClientProvider>
      </App>
    </ConfigProvider>
  );
}

describe('application review page', () => {
  beforeEach(() => {
    vi.mocked(getApplicationApplies).mockResolvedValue({
      items: [], page: 0, size: 20, total: 0
    });
  });

  it('lists pending applies and opens the review modal', async () => {
    vi.mocked(getApplicationApplies).mockResolvedValue({
      page: 0, size: 20, total: 1, items: [pendingApply]
    });
    const user = userEvent.setup();
    renderPage();
    await waitFor(() => expect(screen.getByText('星河收银台')).toBeTruthy());

    expect(screen.getByText('星河便利店')).toBeTruthy();
    expect(screen.getAllByText('待审核').length).toBeGreaterThan(0);

    await user.click(screen.getByRole('button', { name: /^审\s*核$/ }));
    expect(screen.getByText('审核应用申请')).toBeTruthy();
    expect(screen.getByRole('radio', { name: '审核通过' })).toBeTruthy();
    expect(screen.getByRole('radio', { name: '驳回' })).toBeTruthy();
    expect(screen.getByRole('radio', { name: '要求补充资料' })).toBeTruthy();
  });

  it('filters by status tab', async () => {
    const user = userEvent.setup();
    renderPage();
    await waitFor(() => expect(getApplicationApplies).toHaveBeenCalled());

    await user.click(screen.getByRole('button', { name: '已通过' }));
    await waitFor(() => expect(getApplicationApplies).toHaveBeenLastCalledWith({
      page: 0, size: 20, applyStatus: 'APPROVED'
    }));

    await user.click(screen.getByRole('button', { name: /全\s*部/ }));
    await waitFor(() => expect(getApplicationApplies).toHaveBeenLastCalledWith({
      page: 0, size: 20
    }));
  });
});
