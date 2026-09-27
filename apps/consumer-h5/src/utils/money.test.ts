import { describe, expect, it } from 'vitest';
import {
  formatDirectionalFen,
  formatFen,
  formatFenWithSymbol,
  fenToYuanInput,
  parseYuanToFen,
  sanitizeAmountInput,
  validateAmountInput,
  MAX_AMOUNT_FEN
} from './money';

describe('金额工具（全部按整数「分」运算）', () => {
  it('把分格式化为元，且补足两位小数', () => {
    expect(formatFen(0)).toBe('0.00');
    expect(formatFen(1)).toBe('0.01');
    expect(formatFen(123456)).toBe('1,234.56');
    expect(formatFen(100000000)).toBe('1,000,000.00');
    expect(formatFenWithSymbol(123456)).toBe('¥1,234.56');
  });

  it('负数保留符号且不影响整数部分', () => {
    expect(formatFen(-5)).toBe('-0.05');
    expect(formatFen(-123456)).toBe('-1,234.56');
  });

  it('解析元为分时不做浮点运算', () => {
    expect(parseYuanToFen('1')).toBe(100);
    expect(parseYuanToFen('0.1')).toBe(10);
    expect(parseYuanToFen('0.01')).toBe(1);
    expect(parseYuanToFen('1234.56')).toBe(123456);
    // 典型浮点陷阱：0.29 * 100 = 28.999...，字符串解析必须得到 29
    expect(parseYuanToFen('0.29')).toBe(29);
    expect(parseYuanToFen('1.005')).toBeNull();
    expect(parseYuanToFen('abc')).toBeNull();
    expect(parseYuanToFen('1.2.3')).toBeNull();
  });

  it('分转回可编辑输入值', () => {
    expect(fenToYuanInput(123456)).toBe('1234.56');
    expect(fenToYuanInput(5)).toBe('0.05');
  });

  it('校验金额边界', () => {
    expect(validateAmountInput('')).toEqual({ ok: false, reason: 'EMPTY' });
    expect(validateAmountInput('0')).toEqual({ ok: false, reason: 'TOO_SMALL' });
    expect(validateAmountInput('0.00')).toEqual({ ok: false, reason: 'TOO_SMALL' });
    expect(validateAmountInput('x')).toEqual({ ok: false, reason: 'FORMAT' });
    expect(validateAmountInput('0.01')).toEqual({ ok: true, fen: 1 });
    expect(validateAmountInput('10000.00')).toEqual({ ok: true, fen: MAX_AMOUNT_FEN });
    expect(validateAmountInput('10000.01')).toEqual({ ok: false, reason: 'TOO_LARGE' });
  });

  it('输入清洗只保留数字与最多两位小数', () => {
    expect(sanitizeAmountInput('12a.3b45')).toBe('12.34');
    expect(sanitizeAmountInput('0012')).toBe('0012');
    expect(sanitizeAmountInput('.5')).toBe('.5');
  });

  it('方向金额带 + / - 前缀', () => {
    expect(formatDirectionalFen(100, 'CREDIT')).toBe('+¥1.00');
    expect(formatDirectionalFen(100, 'DEBIT')).toBe('-¥1.00');
  });
});
