import { formatDirectionalFen, formatFenWithSymbol } from '../utils/money';
import common from './common.module.less';

export interface AmountTextProps {
  fen: number;
  direction?: string;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
  /** 无障碍朗读文本，避免读屏念出「¥」符号异常。 */
  label?: string;
}

const SIZE_CLASS: Record<NonNullable<AmountTextProps['size']>, string> = {
  sm: common.inlineAmount,
  md: common.amount,
  lg: common.amountLarge
};

/** 金额展示：服务端返回「分」，这里只做整数格式化，不参与任何浮点运算。 */
export function AmountText({ fen, direction, size = 'md', className, label }: AmountTextProps) {
  const text = direction ? formatDirectionalFen(fen, direction) : formatFenWithSymbol(fen);
  return (
    <span
      className={[SIZE_CLASS[size], direction ? (direction === 'DEBIT' ? common.danger : common.success) : '', className]
        .filter(Boolean)
        .join(' ')}
      aria-label={label ?? `金额 ${formatFenWithSymbol(Math.abs(fen))}`}
    >
      {text}
    </span>
  );
}
