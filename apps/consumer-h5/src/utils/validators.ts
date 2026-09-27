/** 中国大陆手机号：`^1[3-9]\d{9}$`（与后端契约一致）。 */
export const MOBILE_PATTERN = /^1[3-9]\d{9}$/;

export function isMobile(value: string): boolean {
  return MOBILE_PATTERN.test(value.trim());
}

/** 支付密码：6 位数字。 */
export const PAY_PASSWORD_LENGTH = 6;
const PAY_PASSWORD_PATTERN = /^\d{6}$/;

export function isPayPassword(value: string): boolean {
  return PAY_PASSWORD_PATTERN.test(value);
}

/** 短信验证码：6 位数字（演示码 123456）。 */
export function isSmsCode(value: string): boolean {
  return /^\d{6}$/.test(value.trim());
}

export function normalizeDigits(value: string, maxLength: number): string {
  return value.replace(/\D/g, '').slice(0, maxLength);
}
