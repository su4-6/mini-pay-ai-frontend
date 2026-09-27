/**
 * 金额工具。金额一律以人民币「分」的整数参与运算，禁止浮点数。
 * 展示层才格式化为「元」，格式化过程同样只使用整数与字符串操作。
 */

const FEN_PATTERN = /^-?\d+$/;
const YUAN_INPUT_PATTERN = /^\d{1,10}(?:\.\d{0,2})?$/;

/** 客户端输入护栏：¥10,000.00。真实上限以服务端返回的 Problem Details 为准。 */
export const MAX_AMOUNT_FEN = 1_000_000;
export const MIN_AMOUNT_FEN = 1;

function groupThousands(digits: string): string {
  return digits.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

/** 把「分」渲染为「元」字符串，例如 123456 → `1,234.56`。 */
export function formatFen(fen: number): string {
  if (!Number.isFinite(fen)) return '--';
  const negative = fen < 0;
  const absolute = Math.abs(Math.trunc(fen));
  const yuan = Math.trunc(absolute / 100);
  const cents = absolute % 100;
  const body = `${groupThousands(String(yuan))}.${String(cents).padStart(2, '0')}`;
  return negative ? `-${body}` : body;
}

/** 带货币符号，例如 123456 → `¥1,234.56`。 */
export function formatFenWithSymbol(fen: number): string {
  const formatted = formatFen(fen);
  return formatted === '--' ? formatted : `¥${formatted}`;
}

/** 账单方向符号：收入为 `+`，支出为 `-`。 */
export function formatDirectionalFen(fen: number, direction: string): string {
  const income = direction === 'CREDIT' || direction === 'IN' || direction === 'INCOME';
  const symbol = income ? '+' : '-';
  return `${symbol}${formatFenWithSymbol(Math.abs(fen))}`;
}

/** 把「分」转成可编辑的「元」输入值：123456 → `1234.56`。 */
export function fenToYuanInput(fen: number): string {
  const absolute = Math.abs(Math.trunc(fen));
  return `${Math.trunc(absolute / 100)}.${String(absolute % 100).padStart(2, '0')}`;
}

/**
 * 把用户输入的「元」解析为「分」。全部使用字符串与整数运算，不经过浮点数。
 * 返回 `null` 表示格式非法。
 */
export function parseYuanToFen(input: string): number | null {
  const normalized = input.trim();
  if (!normalized) return 0;
  if (!YUAN_INPUT_PATTERN.test(normalized)) return null;
  const [yuanPart, fractionPart = ''] = normalized.split('.');
  const cents = `${fractionPart}00`.slice(0, 2);
  const fen = Number(yuanPart) * 100 + Number(cents);
  return FEN_PATTERN.test(String(fen)) ? fen : null;
}

export type AmountValidation =
  | { ok: true; fen: number }
  | { ok: false; reason: 'EMPTY' | 'FORMAT' | 'TOO_SMALL' | 'TOO_LARGE' };

/** 校验转账金额输入。 */
export function validateAmountInput(input: string): AmountValidation {
  const normalized = input.trim();
  if (!normalized) return { ok: false, reason: 'EMPTY' };
  const fen = parseYuanToFen(normalized);
  if (fen === null) return { ok: false, reason: 'FORMAT' };
  if (fen < MIN_AMOUNT_FEN) return { ok: false, reason: 'TOO_SMALL' };
  if (fen > MAX_AMOUNT_FEN) return { ok: false, reason: 'TOO_LARGE' };
  return { ok: true, fen };
}

/** 仅保留数字与一个小数点，且小数位最多两位，用于受控输入框。 */
export function sanitizeAmountInput(raw: string): string {
  const cleaned = raw.replace(/[^\d.]/g, '');
  const [head, ...tail] = cleaned.split('.');
  const integer = head.slice(0, 10);
  if (tail.length === 0) return integer;
  return `${integer}.${tail.join('').slice(0, 2)}`;
}
