import { describe, expect, it } from 'vitest';
import { readFen, readPage, readString } from './parsers';
import { billDirectionLabel, normalizeTransferStatus, transferStatusView } from '../utils/transfer-status';

describe('响应宽松解析', () => {
  it('金额只接受整数分，Fen 优先于 Cent', () => {
    expect(readFen({ amountFen: 123 }, 'amountFen', 'amountCent')).toBe(123);
    expect(readFen({ amountCent: 456 }, 'amountFen', 'amountCent')).toBe(456);
    expect(readFen({ amountFen: 1, amountCent: 2 }, 'amountFen', 'amountCent')).toBe(1);
    expect(readFen({}, 'amountFen')).toBeUndefined();
  });

  it('字符串数字与小数按整数处理', () => {
    expect(readFen({ amountFen: '789' }, 'amountFen')).toBe(789);
    expect(readFen({ amountFen: 12.9 }, 'amountFen')).toBe(12);
  });

  it('读取文本字段时容忍数字', () => {
    expect(readString({ billId: 42 }, 'billId')).toBe('42');
    expect(readString({}, 'billId')).toBeUndefined();
  });

  it('优先使用显式 nextCursor', () => {
    const page = readPage({ items: [{ billId: 'a' }], nextCursor: 'cur-2' });
    expect(page.nextCursor).toBe('cur-2');
  });

  it('没有游标时用最后一条业务 id 作为游标', () => {
    const page = readPage({ items: [{ billId: 'a' }, { billId: 'b' }] });
    expect(page.nextCursor).toBe('b');
  });

  it('支持 page/size/total 分页信封', () => {
    const page = readPage({ items: [{ billId: 'a' }], page: 1, size: 1, total: 3 });
    expect(page.nextCursor).toBe('2');
    const last = readPage({ items: [{ billId: 'c' }], page: 3, size: 1, total: 3 });
    expect(last.nextCursor).toBeUndefined();
  });

  it('hasMore=false 时不产生游标', () => {
    expect(readPage({ items: [{ billId: 'a' }], hasMore: false }).nextCursor).toBeUndefined();
  });
});

describe('转账状态归一化', () => {
  it('兼容 SUCCESS / SUCCEEDED 两种成功写法', () => {
    expect(normalizeTransferStatus('SUCCEEDED')).toBe('SUCCESS');
    expect(normalizeTransferStatus('SUCCESS')).toBe('SUCCESS');
  });

  it('未知状态按处理中处理（不误判成功）', () => {
    expect(normalizeTransferStatus(undefined)).toBe('PROCESSING');
    expect(normalizeTransferStatus('WEIRD')).toBe('PROCESSING');
  });

  it('只有终态才停止轮询', () => {
    expect(transferStatusView('PROCESSING').terminal).toBe(false);
    expect(transferStatusView('SUCCESS').terminal).toBe(true);
    expect(transferStatusView('FAILED').terminal).toBe(true);
    expect(transferStatusView('CLOSED').terminal).toBe(true);
  });

  it('状态文案区分成功/处理中/失败', () => {
    expect(transferStatusView('SUCCESS').label).toBe('转账成功');
    expect(transferStatusView('PROCESSING').label).toBe('转账处理中');
    expect(transferStatusView('FAILED').label).toBe('转账失败');
  });

  it('账单方向文案', () => {
    expect(billDirectionLabel('CREDIT')).toBe('收入');
    expect(billDirectionLabel('DEBIT')).toBe('支出');
    expect(billDirectionLabel('')).toBe('支出');
  });
});
