import { useQuery } from '@tanstack/react-query';
import { Button, Toast } from 'antd-mobile';
import { QRCodeSVG } from 'qrcode.react';
import { AppShell } from '../components/AppShell';
import { AsyncState } from '../components/AsyncState';
import { AuthGate } from '../components/AuthGate';
import { Card } from '../components/Card';
import { InlineNotice } from '../components/InlineNotice';
import { useNow } from '../hooks/useNow';
import { queryKeys } from '../query/keys';
import { fetchCollectionCode } from '../services/wallet';
import { useSession } from '../hooks/useSession';
import { formatDateTime, isExpired, remainingLabel } from '../utils/datetime';
import { maskPhone } from '@minipay/shared';
import styles from './collect.module.less';

function CollectWorkspace() {
  const { profile } = useSession();
  const now = useNow(1_000);
  const query = useQuery({
    queryKey: queryKeys.collectionCode,
    queryFn: fetchCollectionCode,
    staleTime: 30_000
  });

  const code = query.data;
  const expired = isExpired(code?.expiresAt, now);

  async function copyCode(): Promise<void> {
    if (!code?.code) return;
    try {
      await navigator.clipboard.writeText(code.code);
      Toast.show({ content: '收款码内容已复制' });
    } catch {
      Toast.show({ content: '复制失败，请手动选择文本复制' });
    }
  }

  return (
    <AppShell title="我的收款码" subtitle="对方扫码或复制内容后向该账户转账" showTabBar backTo="/wallet">
      <div className={styles.qrPage}>
        <Card>
          <div className={styles.ownerRow}>
            <div className={styles.avatar} aria-hidden>
              {(profile?.displayName ?? 'M').slice(0, 1)}
            </div>
            <div>
              <div className={styles.ownerName}>{profile?.displayName ?? 'MiniPay 用户'}</div>
              <div className={styles.ownerMeta}>
                {profile?.phone ? maskPhone(profile.phone) : '未获取到手机号'}
              </div>
            </div>
          </div>

          <AsyncState
            loading={query.isLoading}
            error={query.isError ? query.error : undefined}
            onRetry={() => void query.refetch()}
            loadingText="正在获取收款码…"
          >
            {code && code.code ? (
              <div>
                <div className={styles.codeText}>
                  {code.qrImageUrl ? (
                    <img
                      src={code.qrImageUrl}
                      alt="我的收款二维码"
                      style={{ width: 220, height: 220, objectFit: 'contain' }}
                    />
                  ) : (
                    <div className={styles.qrWrapper}>
                      <QRCodeSVG
                        value={code.code}
                        size={200}
                        level="M"
                        marginSize={2}
                        title="我的收款二维码"
                      />
                    </div>
                  )}
                </div>
                <p className={styles.codeText} style={{ color: '#667085', fontSize: 12 }}>
                  收款标识
                </p>
                <p className={styles.codeValue} aria-label="收款标识">
                  {code.code}
                </p>
                {expired ? (
                  <InlineNotice tone="warning">该收款码已过期，请刷新获取新的收款码。</InlineNotice>
                ) : code.expiresAt ? (
                  <p style={{ marginTop: 8, color: '#667085', fontSize: 12, textAlign: 'center' }}>
                    {remainingLabel(code.expiresAt, now)} · {formatDateTime(code.expiresAt)} 失效
                  </p>
                ) : null}
                {code.sandboxNotice ? <InlineNotice>{code.sandboxNotice}</InlineNotice> : null}
                <div className={styles.copyRow}>
                  <Button fill="outline" onClick={() => void copyCode()}>
                    复制收款内容
                  </Button>
                  <Button color="primary" onClick={() => void query.refetch()}>
                    {expired ? '刷新收款码' : '重新获取'}
                  </Button>
                </div>
              </div>
            ) : (
              <AsyncState empty emptyText="服务端未返回收款码，请稍后重试" />
            )}
          </AsyncState>
        </Card>

        <InlineNotice>
          H5 端不提供相机扫码与付款码流程：付款请让对方出示其收款码内容，
          或直接在「转账」页输入对方手机号 / MiniPay 号完成转账。
        </InlineNotice>
      </div>
    </AppShell>
  );
}

export default function CollectPage() {
  return (
    <AuthGate>
      <CollectWorkspace />
    </AuthGate>
  );
}
