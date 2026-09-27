import { useLocation } from '@umijs/max';
import { AppShell } from '../components/AppShell';
import { AuthGate } from '../components/AuthGate';
import { InlineNotice } from '../components/InlineNotice';
import { TransferResultPanel } from '../components/TransferResultPanel';
import { readSearchParam } from '../utils/redirect';

function TransferResultWorkspace() {
  const location = useLocation();
  const transferNo = readSearchParam(location.search ?? '', 'transferNo');

  return (
    <AppShell title="转账结果" subtitle="结果以后端转账单状态为准" backTo="/wallet" showTabBar={false}>
      {transferNo ? (
        <TransferResultPanel transferNo={transferNo} />
      ) : (
        <InlineNotice tone="warning">
          缺少转账单号，无法查询结果。请到「转账记录」中查看最近的转账单。
        </InlineNotice>
      )}
    </AppShell>
  );
}

export default function TransferResultPage() {
  return (
    <AuthGate>
      <TransferResultWorkspace />
    </AuthGate>
  );
}
