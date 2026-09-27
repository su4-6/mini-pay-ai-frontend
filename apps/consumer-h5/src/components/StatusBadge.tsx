import common from './common.module.less';
import type { TransferTone } from '../utils/transfer-status';

const TONE_CLASS: Record<TransferTone, string> = {
  success: common.badgeSuccess,
  processing: common.badgeWarning,
  failure: common.badgeDanger,
  closed: ''
};

export interface StatusBadgeProps {
  tone: TransferTone;
  label: string;
}

/** 状态标签：颜色 + 文案，不单靠颜色表达语义。 */
export function StatusBadge({ tone, label }: StatusBadgeProps) {
  return <span className={`${common.badge} ${TONE_CLASS[tone]}`.trim()}>{label}</span>;
}
