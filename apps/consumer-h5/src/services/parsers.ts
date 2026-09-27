/**
 * 响应解析工具。
 *
 * BFF 契约明确规定的字段（`amountFen`、`transferIntentId` …）以契约名称为主；
 * 对于契约只给了路径、没有给字段名的端（钱包、账单、收款码、银行卡），
 * 这里按「Fen 优先、兼容 Cent/其他同义命名」的顺序宽松读取，
 * 避免后端字段命名微调直接把页面打空。宽松读取只做字段映射，
 * 绝不改写金额单位，也绝不把非整数金额转换成分。
 */

export type RawRecord = Record<string, unknown>;

export function asRecord(value: unknown): RawRecord {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as RawRecord) : {};
}

export function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function pick(source: RawRecord, keys: readonly string[]): unknown {
  for (const key of keys) {
    const value = source[key];
    if (value !== undefined && value !== null) return value;
  }
  return undefined;
}

export function readString(source: RawRecord, ...keys: string[]): string | undefined {
  const value = pick(source, keys);
  if (typeof value === 'string') return value;
  if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  return undefined;
}

export function readBoolean(source: RawRecord, ...keys: string[]): boolean | undefined {
  const value = pick(source, keys);
  if (typeof value === 'boolean') return value;
  if (value === 'true') return true;
  if (value === 'false') return false;
  return undefined;
}

/**
 * 读取以「分」为单位的整数字段。只接受整数；遇到小数会取整并保守回退，
 * 因为浮点金额在资金场景下不可信。
 */
export function readFen(source: RawRecord, ...keys: string[]): number | undefined {
  const value = pick(source, keys);
  if (typeof value === 'number' && Number.isFinite(value)) return Math.trunc(value);
  if (typeof value === 'string' && /^-?\d+$/.test(value.trim())) return Number(value.trim());
  return undefined;
}

export function readNested(source: RawRecord, key: string): RawRecord {
  return asRecord(source[key]);
}

/** 分页信封：同时兼容 `{items, nextCursor}` 与 `{items, page, size, total}`。 */
export interface RawPage {
  items: unknown[];
  nextCursor?: string;
}

export function readPage(value: unknown): RawPage {
  const envelope = asRecord(value);
  const items = asArray(envelope.items ?? envelope.records ?? envelope.content ?? envelope.list);
  const explicitCursor = readString(envelope, 'nextCursor', 'next_cursor', 'cursor');
  if (explicitCursor) return { items, nextCursor: explicitCursor };

  const hasMore = readBoolean(envelope, 'hasMore', 'has_more');
  const page = readFen(envelope, 'page');
  const size = readFen(envelope, 'size', 'pageSize', 'limit');
  const total = readFen(envelope, 'total');

  if (hasMore === true && page !== undefined) return { items, nextCursor: String(page + 1) };
  if (hasMore === false) return { items };

  // page/size/total 分页信封：能判定就没有下一页时直接给出结论，
  // 不能回退到「用最后一条 id 当游标」，否则游标永远非空、列表无限加载。
  if (page !== undefined && size !== undefined && size > 0) {
    const consumed = page * size;
    const hasNext = total !== undefined ? consumed < total : items.length >= size;
    return hasNext ? { items, nextCursor: String(page + 1) } : { items };
  }

  // cursor 分页但服务端没给 nextCursor：用最后一条业务 id 兜底。
  if (items.length > 0) {
    const last = asRecord(items[items.length - 1]);
    const fallbackCursor = readString(last, 'billId', 'transferNo', 'id');
    if (fallbackCursor) return { items, nextCursor: fallbackCursor };
  }
  return { items };
}
