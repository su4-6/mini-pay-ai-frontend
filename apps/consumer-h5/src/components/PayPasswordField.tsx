import { Input } from 'antd-mobile';
import { useId } from 'react';
import type { CSSProperties } from 'react';
import { PAY_PASSWORD_LENGTH, normalizeDigits } from '../utils/validators';
import common from './common.module.less';

export interface PayPasswordFieldProps {
  value: string;
  onChange: (value: string) => void;
  /** 输入满 6 位后自动触发（可选）。 */
  onComplete?: (value: string) => void;
  disabled?: boolean;
  label?: string;
  autoFocus?: boolean;
}

/**
 * 支付密码输入框。
 *
 * 安全约束（PROJECT_STANDARDS §4）：
 *   - `type="password"` + `inputMode="numeric"`，不做明文回显；
 *   - 只通过受控 props 传递，绝不写入 localStorage/sessionStorage、URL、日志或 Query 缓存；
 *   - 调用方在提交成功后必须立即清空（见各页面 `setPassword('')`）。
 */
export function PayPasswordField({
  value,
  onChange,
  onComplete,
  disabled,
  label = '支付密码',
  autoFocus
}: PayPasswordFieldProps) {
  const inputId = useId();
  return (
    <div className={common.stack}>
      <label htmlFor={inputId} className={common.rowLabel}>
        {label}
      </label>
      <Input
        id={inputId}
        type="password"
        inputMode="numeric"
        autoComplete="off"
        name="minipay-pay-password"
        placeholder={`请输入 ${PAY_PASSWORD_LENGTH} 位数字支付密码`}
        value={value}
        disabled={disabled}
        autoFocus={autoFocus}
        maxLength={PAY_PASSWORD_LENGTH}
        onChange={(next) => {
          const digits = normalizeDigits(next, PAY_PASSWORD_LENGTH);
          onChange(digits);
          if (digits.length === PAY_PASSWORD_LENGTH) onComplete?.(digits);
        }}
        style={{ '--font-size': '18px', letterSpacing: '6px' } as CSSProperties}
      />
      <p className={common.muted}>
        支付密码仅用于本次资金操作校验，不会保存在本机浏览器中，也不会写入日志或跳转地址。
      </p>
    </div>
  );
}
