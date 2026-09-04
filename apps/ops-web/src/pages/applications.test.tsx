import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ApplicationsPage from './applications';
import {
  changeApplicationStatus,
  fetchAllForExport,
  getApplications,
  getApplicationSummary,
  getMerchants
} from '../services/ops';

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
  getApplications: vi.fn(),
  getApplication: vi.fn(),
  createApplication: vi.fn(),
  updateApplication: vi.fn(),
  changeApplicationStatus: vi.fn(),
  deleteApplication: vi.fn(),
  getMerchant: vi.fn(),
  getMerchants: vi.fn(),
  getApplicationSummary: vi.fn(),
  fetchAllForExport: vi.fn()
}));

const activeApplication = {
  applicationId: 'application-1', appId: 'mp_app_019fb3d0', name: '星河收银台',
  merchantId: 'merchant-1', merchantNo: 'M202608030001', merchantName: '星河便利店',
  merchantStatus: 'ACTIVE', status: 'ACTIVE', hasTransactions: false,
  deletable: false, deletionBlockedReason: 'APPLICATION_MUST_BE_DISABLED',
  recentTransactionCount: 12, lastTransactionAt: '2026-08-02T03:00:00Z',
  createdAt: '2026-08-03T00:00:00Z', updatedAt: '2026-08-03T00:00:00Z', version: 0
} as const;

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <ConfigProvider locale={zhCN}>
      <App>
        <QueryClientProvider client={queryClient}><ApplicationsPage /></QueryClientProvider>
      </App>
    </ConfigProvider>
  );
}

describe('application management page', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', '/applications');
    vi.mocked(getApplications).mockResolvedValue({ items: [], page: 0, size: 20, total: 0 });
    vi.mocked(getMerchants).mockResolvedValue({ items: [], page: 0, size: 20, total: 0 });
    vi.mocked(getApplicationSummary).mockResolvedValue({
      totalCount: 4, activeCount: 3, disabledCount: 1, unavailableCount: 0
    });
  });

  it('supports AppID and name filters with zero-based pagination', async () => {
    const user = userEvent.setup();
    renderPage();
    await waitFor(() => expect(getApplications).toHaveBeenCalled());

    await user.type(screen.getByPlaceholderText('输入 MiniPay AppID'), 'mp_app_01');
    await user.type(screen.getByPlaceholderText('输入应用名称'), '收银台');
    await user.click(screen.getByRole('button', { name: /查\s*询/ }));

    await waitFor(() => expect(getApplications).toHaveBeenLastCalledWith({
      page: 0, size: 20, appId: 'mp_app_01', name: '收银台'
    }));
    expect(screen.queryByText(/Page/)).toBeNull();
    expect(screen.getByText('没有符合条件的应用')).toBeTruthy();
  });

  it('renders availability, recent transactions and the complete create form', async () => {
    vi.mocked(getMerchants).mockResolvedValue({
      page: 0, size: 20, total: 1,
      items: [{
        merchantId: 'merchant-1', merchantNo: 'M202608030001', name: '星河便利店',
        shortName: '星河便利', merchantType: 'ENTERPRISE', profileComplete: true, status: 'ACTIVE',
        accountLinked: true, applicationCount: 1, deletable: false,
        deletionBlockedReason: 'MERCHANT_HAS_APPLICATIONS',
        createdAt: '2026-08-03T00:00:00Z', updatedAt: '2026-08-03T00:00:00Z', version: 0
      }]
    });
    vi.mocked(getApplications).mockResolvedValue({
      page: 0, size: 20, total: 1,
      items: [activeApplication]
    });
    const user = userEvent.setup();
    renderPage();
    await waitFor(() => expect(screen.getByText('mp_app_019fb3d0')).toBeTruthy());

    expect(screen.getByText('星河便利店')).toBeTruthy();
    expect(screen.getByText('已启用', { selector: '.ant-tag' })).toBeTruthy();
    expect(screen.getByText('可用', { selector: '.ant-tag' })).toBeTruthy();
    expect(screen.getByText('12')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: '新建应用' }));

    expect(screen.getByRole('dialog')).toBeTruthy();
    expect(screen.getByLabelText('所属商户')).toBeTruthy();
    expect(screen.getByLabelText('应用名称')).toBeTruthy();
    expect(screen.getByLabelText('初始状态')).toBeTruthy();
  });

  it('renders the platform summary cards', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('应用总数')).toBeTruthy());

    expect(screen.getByText('已启用', { selector: '.ant-statistic-title' })).toBeTruthy();
    expect(screen.getByText('已停用', { selector: '.ant-statistic-title' })).toBeTruthy();
    expect(screen.getByText('不可用', { selector: '.ant-statistic-title' })).toBeTruthy();
    expect(screen.getByText('4')).toBeTruthy();
    expect(screen.getByText('3')).toBeTruthy();
    expect(screen.getByText('75.0%')).toBeTruthy();
  });

  it('batch disables selected applications', async () => {
    vi.mocked(getApplications).mockResolvedValue({
      page: 0, size: 20, total: 1,
      items: [activeApplication]
    });
    vi.mocked(changeApplicationStatus).mockResolvedValue({
      ...activeApplication, status: 'DISABLED'
    });
    const user = userEvent.setup();
    renderPage();
    await waitFor(() => expect(screen.getByText('mp_app_019fb3d0')).toBeTruthy());

    await user.click(screen.getAllByRole('checkbox')[1]);
    expect(screen.getByText('已选择 1 项')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: '批量停用' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: /停\s*用/ }));

    await waitFor(() => expect(changeApplicationStatus).toHaveBeenCalledWith(
      expect.objectContaining({ applicationId: 'application-1' }),
      'DISABLED',
      expect.any(String)
    ));
  });

  it('exports the current filter result as CSV', async () => {
    const createObjectURL = vi.fn(() => 'blob:mock');
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL: vi.fn() });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    vi.mocked(fetchAllForExport).mockResolvedValue({
      items: [activeApplication], total: 1, truncated: false
    });
    const user = userEvent.setup();
    renderPage();
    await waitFor(() => expect(getApplications).toHaveBeenCalled());

    await user.click(screen.getByRole('button', { name: '导出 CSV' }));

    await waitFor(() => expect(fetchAllForExport).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 100 })
    ));
    expect(click).toHaveBeenCalled();
    vi.unstubAllGlobals();
  });
});
