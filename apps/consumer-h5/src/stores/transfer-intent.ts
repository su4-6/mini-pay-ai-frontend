import { create } from 'zustand';
import type { PreparedTransfer } from '../types/consumer';

/**
 * 转账流程的短期状态：prepare 成功后、confirm 之前的转账意图快照。
 *
 * 只放在内存里，绝不写入 localStorage / sessionStorage / URL Query / Query 缓存：
 * 意图包含收款方与金额，属于资金操作上下文；页面刷新即丢弃，
 * 用户可通过 `GET /api/v1/transfers/{transferNo}` 查询已提交的转账单恢复。
 */
export interface TransferFlowState {
  prepared: PreparedTransfer | null;
  setPrepared: (prepared: PreparedTransfer) => void;
  clear: () => void;
}

export const useTransferStore = create<TransferFlowState>((set) => ({
  prepared: null,
  setPrepared: (prepared) => set({ prepared }),
  clear: () => set({ prepared: null })
}));
