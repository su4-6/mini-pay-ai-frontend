import { Link, useNavigate } from '@umijs/max';
import { useQuery } from '@tanstack/react-query';
import { Button } from 'antd-mobile';
import {
  BillOutline,
  LockOutline,
  PayCircleOutline,
  ReceivePaymentOutline,
  RightOutline,
  ScanningOutline,
  UnorderedListOutline
} from 'antd-mobile-icons';
import { AmountText } from '../components/AmountText';
import { AppShell } from '../components/AppShell';
import { AsyncState } from '../components/AsyncState';
import { AuthGate } from '../components/AuthGate';
import { Card } from '../components/Card';
import { InlineNotice } from '../components/InlineNotice';
import { ROUTES } from '../constants/routes';
import { useSession } from '../hooks/useSession';
import { queryKeys } from '../query/keys';
import { fetchBillPage, fetchWallet } from '../services/wallet';
import { formatDateTime } from '../utils/datetime';
import { formatFenWithSymbol } from '../utils/money';
import { billDirectionLabel } from '../utils/transfer-status';
import styles from './wallet.module.less';

const PREVIEW_BILL_COUNT = 5;

function WalletWorkspace() {
  const navigate = useNavigate();
  const { payPasswordSet } = useSession();

  const walletQuery = useQuery({
    queryKey: queryKeys.wallet,
    queryFn: fetchWallet
  });

  const billsQuery = useQuery({
    queryKey: queryKeys.billPreview,
    queryFn: () => fetchBillPage(null, PREVIEW_BILL_COUNT)
  });

  const wallet = walletQuery.data;

  return (
    <AppShell
      title="钱包"
      subtitle="余额与账单以服务端为准，金额单位为人民币分"
      backTo={ROUTES.home}
      showTabBar
    >
      {!payPasswordSet ? (
        <div style={{ marginBottom: 12 }}>
          <InlineNotice tone="warning">
            你还没有设置支付密码，无法发起转账。
            <Button
              size="mini"
              color="primary"
              fill="none"
              onClick={() => navigate(ROUTES.payPassword)}
            >
              去设置
            </Button>
          </InlineNotice>
        </div>
      ) : null}

      <section className={styles.balanceCard} aria-label="钱包余额">
        <p className={styles.balanceLabel}>可用余额（元）</p>
        {walletQuery.isLoading ? (
          <p className={styles.balanceValue}>--</p>
        ) : walletQuery.isError ? (
          <div>
            <p className={styles.balanceValue}>--</p>
            <Button size="mini" fill="outline" onClick={() => void walletQuery.refetch()}>
              重新加载余额
            </Button>
          </div>
        ) : (
          <p className={styles.balanceValue}>
            <AmountText fen={wallet?.availableFen ?? 0} size="lg" />
          </p>
        )}
        <div className={styles.balanceMeta}>
          <span>冻结 {wallet ? formatFenWithSymbol(wallet.frozenFen) : '--'}</span>
          <span>总额 {wallet ? formatFenWithSymbol(wallet.totalFen) : '--'}</span>
          <span>{wallet?.currency ?? 'CNY'}</span>
        </div>
        {wallet?.sandboxNotice ? (
          <p style={{ marginTop: 10, fontSize: 11, opacity: 0.85 }}>{wallet.sandboxNotice}</p>
        ) : null}
      </section>

      <Card title="快捷操作" tight>
        <nav className={styles.quickGrid} aria-label="钱包快捷操作">
          <Link to={ROUTES.transfer} className={styles.quickItem}>
            <PayCircleOutline className={styles.quickIcon} aria-hidden />
            <span>转账</span>
          </Link>
          <Link to={ROUTES.collect} className={styles.quickItem}>
            <ReceivePaymentOutline className={styles.quickIcon} aria-hidden />
            <span>收款码</span>
          </Link>
          <Link to={ROUTES.pay} className={styles.quickItem}>
            <ScanningOutline className={styles.quickIcon} aria-hidden />
            <span>扫码付款</span>
          </Link>
          <Link to={ROUTES.bills} className={styles.quickItem}>
            <BillOutline className={styles.quickIcon} aria-hidden />
            <span>账单</span>
          </Link>
          <Link to={ROUTES.transfers} className={styles.quickItem}>
            <UnorderedListOutline className={styles.quickIcon} aria-hidden />
            <span>转账记录</span>
          </Link>
          <Link to={ROUTES.payPassword} className={styles.quickItem}>
            <LockOutline className={styles.quickIcon} aria-hidden />
            <span>支付密码</span>
          </Link>
        </nav>
      </Card>

      <Card
        title="最近账单"
        action={
          <Button size="mini" fill="none" onClick={() => navigate(ROUTES.bills)}>
            查看全部 <RightOutline aria-hidden />
          </Button>
        }
        tight
      >
        <AsyncState
          loading={billsQuery.isLoading}
          error={billsQuery.isError ? billsQuery.error : undefined}
          empty={(billsQuery.data?.items.length ?? 0) === 0}
          emptyText="暂无账单记录"
          onRetry={() => void billsQuery.refetch()}
        >
          <ul>
            {billsQuery.data?.items.map((bill) => (
              <li key={bill.billId || bill.businessNo}>
                <div className={styles.billRow}>
                  <div className={styles.billMain}>
                    <div className={styles.billTitle}>{bill.counterpartyDisplay ?? bill.businessType}</div>
                    <div className={styles.billMeta}>
                      {billDirectionLabel(bill.direction)} · {formatDateTime(bill.occurredAt)}
                    </div>
                  </div>
                  <AmountText fen={bill.amountFen} direction={bill.direction} size="sm" />
                </div>
              </li>
            ))}
          </ul>
        </AsyncState>
      </Card>

      <div className={styles.quickGrid} style={{ gridTemplateColumns: '1fr 1fr' }}>
        <Button color="primary" onClick={() => navigate(ROUTES.transfer)}>
          发起转账
        </Button>
        <Button fill="outline" onClick={() => navigate(ROUTES.collect)}>
          我的收款码
        </Button>
      </div>
    </AppShell>
  );
}

export default function WalletPage() {
  return (
    <AuthGate>
      <WalletWorkspace />
    </AuthGate>
  );
}
