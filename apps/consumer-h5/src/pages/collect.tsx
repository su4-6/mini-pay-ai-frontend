import { useNavigate } from '@umijs/max';
import { useQuery } from '@tanstack/react-query';
import { Button, Toast } from 'antd-mobile';
import { CheckShieldOutline, ShopbagOutline, UserOutline } from 'antd-mobile-icons';
import { QRCodeSVG } from 'qrcode.react';
import { useEffect, useState } from 'react';
import { AppShell } from '../components/AppShell';
import { AsyncState } from '../components/AsyncState';
import { AuthGate } from '../components/AuthGate';
import { InlineNotice } from '../components/InlineNotice';
import { ROUTES } from '../constants/routes';
import { useNow } from '../hooks/useNow';
import { useSession } from '../hooks/useSession';
import { queryKeys } from '../query/keys';
import { fetchBusinessCollectionCode, fetchMerchantCenter } from '../services/merchant';
import { fetchCollectionCode } from '../services/wallet';
import { isExpired, remainingLabel } from '../utils/datetime';
import { maskPhone } from '@minipay/shared';
import styles from './collect.module.less';

type CodeMode = 'personal' | 'merchant';

function CollectWorkspace() {
  const navigate = useNavigate();
  const { profile } = useSession();
  const now = useNow(1_000);
  const [mode, setMode] = useState<CodeMode>('personal');
  const [merchantId, setMerchantId] = useState('');
  const personalQuery = useQuery({ queryKey: queryKeys.collectionCode, queryFn: fetchCollectionCode, staleTime: 30_000 });
  const merchantQuery = useQuery({ queryKey: queryKeys.merchantCenter, queryFn: fetchMerchantCenter, staleTime: 20_000 });
  const readyMerchants = merchantQuery.data?.merchants.filter((item) => item.status === 'ACTIVE' && item.initialized) ?? [];
  useEffect(() => {
    if (!readyMerchants.length) { setMerchantId(''); return; }
    if (!readyMerchants.some((item) => item.merchantId === merchantId)) setMerchantId(readyMerchants[0].merchantId);
  }, [merchantId, readyMerchants]);
  const merchant = readyMerchants.find((item) => item.merchantId === merchantId) ?? readyMerchants[0];
  const merchantReady = Boolean(merchant?.initialized);
  const businessCodeQuery = useQuery({
    queryKey: queryKeys.businessCollectionCode(merchant?.merchantId ?? ''),
    queryFn: () => fetchBusinessCollectionCode(merchant!.merchantId),
    enabled: merchantReady && Boolean(merchant?.merchantId),
    staleTime: 30_000
  });
  const personal = personalQuery.data;
  const business = businessCodeQuery.data;
  const currentCode = mode === 'merchant' ? business?.code : personal?.code;
  const expired = mode === 'personal' && isExpired(personal?.expiresAt, now);
  const currentQuery = mode === 'merchant' ? businessCodeQuery : personalQuery;

  async function copyCode(): Promise<void> {
    if (!currentCode) return;
    try {
      await navigator.clipboard.writeText(currentCode);
      Toast.show({ content: '收款内容已复制' });
    } catch {
      Toast.show({ content: '复制失败，请使用系统分享或重新扫码' });
    }
  }

  return (
    <AppShell title="我的收款码" subtitle="个人转账与商户收款，共用同一个钱包" showTabBar backTo={ROUTES.home}>
      <div className={styles.page}>
        <section className={styles.hero}>
          <div className={styles.owner}>
            <div className={styles.avatar}>{(profile?.displayName ?? 'M').slice(0, 1).toUpperCase()}</div>
            <div>
              <strong>{mode === 'merchant' ? business?.merchantName ?? merchant?.name : profile?.displayName ?? 'MiniPay 用户'}</strong>
              <span>{mode === 'merchant' ? 'MiniPay 认证商户' : profile?.phone ? maskPhone(profile.phone) : '个人账户'}</span>
            </div>
            <div className={styles.verified}><CheckShieldOutline /> 安全收款</div>
          </div>

          {merchantReady ? (
            <div className={styles.switcher} role="tablist" aria-label="收款码类型">
              <button type="button" role="tab" aria-selected={mode === 'personal'} className={mode === 'personal' ? styles.active : ''} onClick={() => setMode('personal')}><UserOutline /> 个人收款</button>
              <button type="button" role="tab" aria-selected={mode === 'merchant'} className={mode === 'merchant' ? styles.active : ''} onClick={() => setMode('merchant')}><ShopbagOutline /> 商户收款</button>
            </div>
          ) : null}

          {mode === 'merchant' && readyMerchants.length > 1 ? (
            <label className={styles.storePicker}>收款门店
              <select value={merchant?.merchantId ?? ''} onChange={(event) => setMerchantId(event.target.value)}>
                {readyMerchants.map((item) => <option key={item.merchantId} value={item.merchantId}>{item.name} · {item.merchantNo}</option>)}
              </select>
            </label>
          ) : null}

          <AsyncState loading={currentQuery.isLoading} error={currentQuery.isError ? currentQuery.error : undefined} onRetry={() => void currentQuery.refetch()} loadingText="正在生成安全收款码…">
            {currentCode ? (
              <div className={styles.qrStage}>
                <div className={styles.qrFrame}>
                  <QRCodeSVG value={currentCode} size={222} level="M" marginSize={2} title={mode === 'merchant' ? '商户收款二维码' : '个人收款二维码'} />
                  <span className={styles.qrMark}>M</span>
                </div>
                <strong className={styles.modeTitle}>{mode === 'merchant' ? '商户收款码' : '个人收款码'}</strong>
                <p>{mode === 'merchant' ? '对方扫码后进入商户付款确认' : '对方扫码后进入个人转账确认'}</p>
                {mode === 'personal' && personal?.expiresAt ? (
                  <div className={`${styles.expiry} ${expired ? styles.expired : ''}`}>{expired ? '已失效，请立即刷新' : remainingLabel(personal.expiresAt, now)}</div>
                ) : <div className={styles.expiry}>商户码长期有效，可在商户工作台管理</div>}
              </div>
            ) : <AsyncState empty emptyText="暂未获取到收款码" />}
          </AsyncState>

          <div className={styles.actions}>
            <Button fill="outline" disabled={!currentCode} onClick={() => void copyCode()}>复制收款内容</Button>
            <Button color="primary" onClick={() => void currentQuery.refetch()}>{expired ? '刷新收款码' : '重新获取'}</Button>
          </div>
        </section>

        {!merchantReady ? (
          <button type="button" className={styles.merchantBanner} onClick={() => navigate(ROUTES.merchant)}>
            <span className={styles.bannerIcon}><ShopbagOutline /></span>
            <span><strong>开通商户收款码</strong><small>与个人账户共用余额，申请状态与商户端同步</small></span>
            <b>去开通 →</b>
          </button>
        ) : null}

        <div className={styles.tips}>
          <div><span>01</span><p><strong>扫码先看收款方</strong>付款前会展示个人或商户名称，请认真核对。</p></div>
          <div><span>02</span><p><strong>资金仍需确认</strong>扫码不会直接扣款，必须填写金额并输入支付密码。</p></div>
        </div>
        {mode === 'personal' && personal?.sandboxNotice ? <InlineNotice>{personal.sandboxNotice}</InlineNotice> : null}
      </div>
    </AppShell>
  );
}

export default function CollectPage() {
  return <AuthGate><CollectWorkspace /></AuthGate>;
}
