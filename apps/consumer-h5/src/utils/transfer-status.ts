import type { TransferStatus } from '../types/consumer';

export type TransferTone = 'success' | 'processing' | 'failure' | 'closed';

export interface TransferStatusView {
  tone: TransferTone;
  label: string;
  description: string;
  /** 终态之后不再轮询，也不再允许重试确认。 */
  terminal: boolean;
}

const SUCCESS_STATUSES = ['SUCCESS', 'SUCCEEDED'];
const PROCESSING_STATUSES = ['PROCESSING', 'PENDING', 'PENDING_CONFIRM', 'PENDING_CONFIRMATION', 'CREATED'];
const CLOSED_STATUSES = ['CLOSED', 'CANCELLED', 'CANCELED', 'EXPIRED'];

export function normalizeTransferStatus(status: string | undefined): TransferStatus {
  const upper = (status ?? '').toUpperCase();
  if (SUCCESS_STATUSES.includes(upper)) return 'SUCCESS';
  if (CLOSED_STATUSES.includes(upper)) return upper === 'CANCELED' ? 'CANCELLED' : (upper as TransferStatus);
  if (PROCESSING_STATUSES.includes(upper)) return 'PROCESSING';
  return upper === 'FAILED' ? 'FAILED' : 'PROCESSING';
}

export function transferStatusView(status: string | undefined): TransferStatusView {
  const normalized = normalizeTransferStatus(status);
  switch (normalized) {
    case 'SUCCESS':
      return {
        tone: 'success',
        label: '转账成功',
        description: '款项已从你的钱包余额扣除，可在账单中查看明细。',
        terminal: true
      };
    case 'FAILED':
      return {
        tone: 'failure',
        label: '转账失败',
        description: '本次转账未完成。如余额已冻结，系统会自动解冻，请稍后在账单核对。',
        terminal: true
      };
    case 'CLOSED':
      return {
        tone: 'closed',
        label: '转账已关闭',
        description: '转账已关闭或超时未确认，资金未发生变化。',
        terminal: true
      };
    case 'CANCELLED':
      return {
        tone: 'closed',
        label: '转账已取消',
        description: '你已取消本次转账，资金未发生变化。',
        terminal: true
      };
    case 'EXPIRED':
      return {
        tone: 'closed',
        label: '转账已超时',
        description: '确认超时，转账意图已失效，请重新发起。',
        terminal: true
      };
    default:
      return {
        tone: 'processing',
        label: '转账处理中',
        description: '后端仍在处理，可下拉刷新或稍后重新查询，请勿重复发起。',
        terminal: false
      };
  }
}

/** 账单方向的展示语义。 */
export function billDirectionLabel(direction: string): string {
  const upper = direction.toUpperCase();
  if (upper === 'CREDIT' || upper === 'IN' || upper === 'INCOME') return '收入';
  return '支出';
}
