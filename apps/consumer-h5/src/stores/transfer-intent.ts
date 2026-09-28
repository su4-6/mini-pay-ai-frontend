import { create } from 'zustand';
import type { PreparedTransfer } from '../types/consumer';

/**
 * 转账流程的短期状态：AI 建议形成的待核对草稿，以及 prepare 成功后、confirm 之前的转账意图快照。
 *
 * 只放在内存里，绝不写入 localStorage / sessionStorage / URL Query / Query 缓存：
 * 意图包含收款方与金额，属于资金操作上下文；页面刷新即丢弃，
 * 用户可通过 `GET /api/v1/transfers/{transferNo}` 查询已提交的转账单恢复。
 */
export interface TransferFlowState {
  prepared: PreparedTransfer | null;
  draft: TransferDraft | null;
  setPrepared: (prepared: PreparedTransfer) => void;
  setDraft: (draft: TransferDraft) => void;
  clearDraft: () => void;
  clear: () => void;
}

export interface TransferDraft {
  payeeIdentifier: string;
  amountFen: number;
}

export const useTransferStore = create<TransferFlowState>((set) => ({
  prepared: null,
  draft: null,
  setPrepared: (prepared) => set({ prepared }),
  setDraft: (draft) => set({ draft }),
  clearDraft: () => set({ draft: null }),
  clear: () => set({ prepared: null, draft: null })
}));
