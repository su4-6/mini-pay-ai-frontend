import { useNavigate } from '@umijs/max';
import { useQuery } from '@tanstack/react-query';
import { Button } from 'antd-mobile';
import { CheckCircleOutline, ClockCircleOutline, ExclamationCircleOutline } from 'antd-mobile-icons';
import type { ReactNode } from 'react';
import { useRef } from 'react';
import { ROUTES } from '../constants/routes';
import { useNow } from '../hooks/useNow';
import { queryKeys } from '../query/keys';
import { fetchTransferDetail } from '../services/transfers';
import { describeFailureCode } from '../services/problem';
import { formatDateTime } from '../utils/datetime';
import { transferStatusView } from '../utils/transfer-status';
import { AmountText } from './AmountText';
import { AsyncState } from './AsyncState';
import { Card } from './Card';
import { KeyValueRow } from './KeyValueRow';
import { StatusBadge } from './StatusBadge';
import common from './common.module.less';

const POLL_INTERVAL_MS = 3_000;
/** 处理中超过该时长后停止自动轮询，改为提示用户手动重新查询（避免无限轮询）。 */
const POLL_TIMEOUT_MS = 60_000;

export interface TransferResultPanelProps {
  transferNo: string;
  extraActions?: ReactNode;
}

function ToneIcon({ tone }: { tone: 'success' | 'processing' | 'failure' | 'closed' }) {
  if (tone === 'success') return <CheckCircleOutline fontSize={34} className={common.success} aria-hidden />;
  if (tone === 'processing') return <ClockCircleOutline fontSize={34} className={common.warning} aria-hidden />;
  return <ExclamationCircleOutline fontSize={34} className={common.danger} aria-hidden />;
}

/**
 * 转账结果 / 转账单详情共用面板。
 *
 * 结果一律以后端 `GET /api/v1/transfers/{transferNo}` 为准：
 * PROCESSING 时轮询（最多 60s），终态后停止；超时不判定成功或失败，
 * 只提示用户稍后重新查询，绝不前端伪造结果。
 */
export function TransferResultPanel({ transferNo, extraActions }: TransferResultPanelProps) {
  const navigate = useNavigate();
  const startedAt = useRef(Date.now());
  const now = useNow(1_000);
  const query = useQuery({
    queryKey: queryKeys.transferDetail(transferNo),
    queryFn: () => fetchTransferDetail(transferNo),
    retry: 1,
    refetchInterval: (query) => {
      const detail = query.state.data;
      if (!detail) return false;
      if (transferStatusView(detail.status).terminal) return false;
      return Date.now() - startedAt.current > POLL_TIMEOUT_MS ? false : POLL_INTERVAL_MS;
    }
  });

  const detail = query.data;
  const view = detail ? transferStatusView(detail.status) : undefined;
  const polling = Boolean(view && !view.terminal) && now - startedAt.current <= POLL_TIMEOUT_MS;
  const timedOut = Boolean(view && !view.terminal) && now - startedAt.current > POLL_TIMEOUT_MS;

  return (
    <AsyncState
      loading={query.isLoading}
      error={query.isError ? query.error : undefined}
      onRetry={() => void query.refetch()}
      loadingText="正在查询转账结果…"
    >
      {detail && view ? (
        <div className={common.stack}>
          <Card>
            <div className={common.centerBox} style={{ border: 'none', boxShadow: 'none', padding: '8px 0' }}>
              <ToneIcon tone={view.tone} />
              <h2 style={{ fontSize: 18 }}>{view.label}</h2>
              <AmountText fen={detail.amountFen} size="lg" />
              <StatusBadge tone={view.tone} label={view.label} />
            </div>
            <p className={common.muted} style={{ textAlign: 'center' }}>
              {view.description}
            </p>
            {polling ? (
              <p className={common.muted} style={{ textAlign: 'center' }} role="status" aria-live="polite">
                正在自动刷新结果…
              </p>
            ) : null}
            {timedOut ? (
              <div className={common.noticeWarning} style={{ marginTop: 12 }}>
                <div className={common.noticeBody}>
                  后端仍在处理中。为避免重复扣款，请勿重新发起同一笔转账；
                  可稍后点击「重新查询」或到转账记录查看最终状态。
                </div>
              </div>
            ) : null}
          </Card>

          <Card title="转账信息" tight>
            <dl style={{ margin: 0 }}>
              <KeyValueRow label="转账单号" value={detail.transferNo || transferNo} mono />
              {detail.payeeMasked ? <KeyValueRow label="收款人" value={detail.payeeMasked} /> : null}
              {detail.payerMasked ? <KeyValueRow label="付款人" value={detail.payerMasked} /> : null}
              <KeyValueRow label="金额" value={<AmountText fen={detail.amountFen} size="sm" />} />
              {detail.remark ? <KeyValueRow label="备注" value={detail.remark} /> : null}
              {detail.createdAt ? <KeyValueRow label="创建时间" value={formatDateTime(detail.createdAt)} /> : null}
              {detail.updatedAt ? <KeyValueRow label="更新时间" value={formatDateTime(detail.updatedAt)} /> : null}
              {detail.failureCode ? (
                <KeyValueRow label="失败原因" value={describeFailureCode(detail.failureCode)} />
              ) : null}
            </dl>
          </Card>

          <div className={common.actions}>
            <Button color="primary" fill="outline" onClick={() => void query.refetch()}>
              重新查询
            </Button>
            <Button fill="outline" onClick={() => navigate(ROUTES.transfers)}>
              转账记录
            </Button>
            <Button color="primary" onClick={() => navigate(ROUTES.transfer)}>
              再次转账
            </Button>
          </div>
          {extraActions}
        </div>
      ) : (
        <AsyncState empty emptyText="未查询到该转账单" />
      )}
    </AsyncState>
  );
}
