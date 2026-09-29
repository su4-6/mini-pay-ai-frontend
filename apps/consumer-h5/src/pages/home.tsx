import { Link } from '@umijs/max';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { ArrowDownToLine, ArrowRight, ArrowUpFromLine, Bot, CreditCard, QrCode, ReceiptText, ScanLine, Send } from 'lucide-react';
import { AppShell } from '../components/AppShell';
import { AuthGate } from '../components/AuthGate';
import { InlineNotice } from '../components/InlineNotice';
import { ROUTES } from '../constants/routes';
import { useSession } from '../hooks/useSession';
import { queryKeys } from '../query/keys';
import { fetchBillPage, fetchWallet } from '../services/wallet';
import { formatDateTime } from '../utils/datetime';
import { formatFenWithSymbol } from '../utils/money';
import styles from './home.module.less';

const primaryActions = [
  { label: '扫一扫', path: ROUTES.pay, Icon: ScanLine, tone: 'violet' },
  { label: '收款', path: ROUTES.collect, Icon: QrCode, tone: 'cyan' },
  { label: '转账', path: ROUTES.transfer, Icon: Send, tone: 'blue' }
] as const;

const actions = [
  { label: '充值', path: `${ROUTES.funding}?type=RECHARGE`, Icon: ArrowDownToLine, tone: 'green' },
  { label: '提现', path: `${ROUTES.funding}?type=WITHDRAWAL`, Icon: ArrowUpFromLine, tone: 'orange' },
  { label: '银行卡', path: ROUTES.bankCards, Icon: CreditCard, tone: 'blue' },
  { label: '米灵', path: ROUTES.chat, Icon: Bot, tone: 'indigo' }
] as const;

function HomeWorkspace() {
  const [showBalance, setShowBalance] = useState(false);
  const { profile } = useSession();
  const needsRealName = !profile?.realNameVerified;
  const needsPayPassword = !profile?.payPasswordSet;
  const wallet = useQuery({ queryKey: queryKeys.wallet, queryFn: fetchWallet, enabled: !needsRealName });
  const bills = useQuery({
    queryKey: queryKeys.billPreview,
    queryFn: () => fetchBillPage(null, 3),
    enabled: !needsRealName
  });
  const hour = new Date().getHours();
  const greeting = hour < 6 ? '夜深了' : hour < 12 ? '上午好' : hour < 18 ? '下午好' : '晚上好';

  return (
    <AppShell title="MiniPay" showTabBar headerless>
      <section className={styles.hero}>
        <div className={styles.heroTop}>
          <div>
            <p className={styles.brandLine}><span className={styles.miniMark}>M</span> MiniPay <em>沙箱钱包</em></p>
            <p className={styles.eyebrow}>{greeting}，{profile?.displayName || 'MiniPay 用户'}</p>
          </div>
          <Link to={ROUTES.me} className={styles.avatar} aria-label="进入个人中心">
            {(profile?.displayName || 'M').slice(0, 1).toUpperCase()}
          </Link>
        </div>
        <div className={styles.primaryGrid}>
          {primaryActions.map(({ label, path, Icon }) => (
            <Link key={label} to={path} className={styles.primaryAction}>
              <Icon /><strong>{label}</strong>
            </Link>
          ))}
        </div>
      </section>

      {needsRealName || needsPayPassword ? (
        <div className={styles.noticeWrap}>
          <InlineNotice tone="warning">
            <div>
              <strong>完成账户设置后即可使用全部资金功能</strong>
              <p className={styles.noticeText}>
                {needsRealName ? '还需实名认证' : ''}{needsRealName && needsPayPassword ? '、' : ''}
                {needsPayPassword ? '设置支付密码' : ''}
              </p>
            </div>
            <Link className={styles.noticeLink} to={needsRealName ? ROUTES.realName : ROUTES.payPassword}>去完成</Link>
          </InlineNotice>
        </div>
      ) : null}

      <section className={styles.assetCard}>
        <div>
          <span className={styles.assetLabel}>我的钱包</span>
          <strong className={styles.assetValue}>
            {needsRealName ? '待开通' : showBalance ? (wallet.isLoading ? '加载中' : wallet.isError ? '暂不可用' : formatFenWithSymbol(wallet.data?.availableFen ?? 0)) : '••••••'}
          </strong>
          <small>{needsRealName ? '完成实名后开通' : '可用余额'}</small>
        </div>
        <div className={styles.assetActions}>
          {!needsRealName ? <button type="button" onClick={() => setShowBalance((value) => !value)}>{showBalance ? '隐藏金额' : '查看金额'}</button> : null}
          <Link to={ROUTES.wallet}>钱包详情 <ArrowRight /></Link>
        </div>
      </section>

      <section className={styles.section}>
        <div className={styles.sectionHeader}><h2>更多服务</h2></div>
        <div className={styles.actionGrid}>
          {actions.map(({ label, path, Icon, tone }) => (
            <Link key={label} to={path} className={styles.actionItem}>
              <span className={`${styles.actionIcon} ${styles[tone]}`}><Icon /></span>
              <span className={styles.actionCopy}><strong>{label}</strong></span>
            </Link>
          ))}
        </div>
      </section>

      <Link to={ROUTES.chat} className={styles.aiBanner}>
        <span className={styles.aiIcon}><Bot /></span>
        <span><strong>有问题，问米灵</strong><small>账单解读、功能导航与沙箱使用提示</small></span>
        <ArrowRight />
      </Link>

      <section className={styles.section}>
        <div className={styles.sectionHeader}>
          <h2>最近账单</h2><Link to={ROUTES.bills}>查看全部 <ArrowRight /></Link>
        </div>
        {bills.isLoading ? <div className={styles.skeleton}>正在加载账单…</div> : null}
        {bills.isError ? <button className={styles.retry} onClick={() => void bills.refetch()}>账单加载失败，点此重试</button> : null}
        {!bills.isLoading && !bills.isError && (bills.data?.items.length ?? 0) === 0 ? (
          <div className={styles.empty}>
            {needsRealName
              ? '完成沙箱实名认证后，这里会展示钱包账单。'
              : '还没有账单，完成一笔转账或沙箱资金操作后会显示在这里。'}
          </div>
        ) : null}
        <div className={styles.billList}>
          {bills.data?.items.map((bill) => (
            <div className={styles.bill} key={bill.billId}>
              <div className={styles.billIcon}><ReceiptText /></div>
              <div className={styles.billMain}>
                <strong>{bill.counterpartyDisplay || bill.remark || bill.businessType}</strong>
                <small>{formatDateTime(bill.occurredAt)}</small>
              </div>
              <span className={bill.direction === 'CREDIT' ? styles.credit : styles.debit}>
                {bill.direction === 'CREDIT' ? '+' : '-'}{formatFenWithSymbol(bill.amountFen)}
              </span>
            </div>
          ))}
        </div>
      </section>
    </AppShell>
  );
}

export default function HomePage() {
  return <AuthGate><HomeWorkspace /></AuthGate>;
}
