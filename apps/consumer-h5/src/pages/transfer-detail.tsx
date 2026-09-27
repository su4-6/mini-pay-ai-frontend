import { useLocation } from '@umijs/max';
import { AppShell } from '../components/AppShell';
import { AuthGate } from '../components/AuthGate';
import { InlineNotice } from '../components/InlineNotice';
import { TransferResultPanel } from '../components/TransferResultPanel';
import { readSearchParam } from '../utils/redirect';

function TransferDetailWorkspace() {
  const location = useLocation();
  const transferNo = readSearchParam(location.search ?? '', 'transferNo');

  return (
    <AppShell title="转账详情" subtitle="可直接按转账单号重新查询最新状态" backTo="/transfers">
      {transferNo ? (
        <TransferResultPanel transferNo={transferNo} />
      ) : (
        <InlineNotice tone="warning">缺少转账单号，请从「转账记录」进入详情。</InlineNotice>
      )}
    </AppShell>
  );
}

export default function TransferDetailPage() {
  return (
    <AuthGate>
      <TransferDetailWorkspace />
    </AuthGate>
  );
}
