import { create } from 'zustand';
import type { AiStreamPhase } from '../types/consumer';

/**
 * 只保存客户端短期交互状态：会话视图、草稿与流式缓冲。
 * 服务端数据（会话列表、消息、钱包、转账单）一律由 TanStack Query 管理。
 * 这里绝不保存支付密码、令牌或任何可持久化的敏感信息。
 */
export interface ChatUiState {
  activeConversationId: string | null;
  draft: string;
  streamPhase: AiStreamPhase;
  streamRunId: string | null;
  streamedText: string;
  streamError: string | null;
  lastEventId: string | null;
  setActiveConversation: (conversationId: string | null) => void;
  setDraft: (draft: string) => void;
  beginStream: (conversationId: string, runId: string) => void;
  appendDelta: (runId: string, text: string, eventId: string | null) => void;
  setEventCursor: (eventId: string | null) => void;
  markReconnecting: (runId: string) => void;
  markFailed: (runId: string, message: string) => void;
  finishStream: (runId: string) => void;
  resetStream: () => void;
}

export const useChatStore = create<ChatUiState>((set, get) => ({
  activeConversationId: null,
  draft: '',
  streamPhase: 'IDLE',
  streamRunId: null,
  streamedText: '',
  streamError: null,
  lastEventId: null,

  setActiveConversation: (conversationId) =>
    set({
      activeConversationId: conversationId,
      draft: '',
      streamPhase: 'IDLE',
      streamRunId: null,
      streamedText: '',
      streamError: null,
      lastEventId: null
    }),

  setDraft: (draft) => set({ draft }),

  beginStream: (conversationId, runId) =>
    set({
      activeConversationId: conversationId,
      streamPhase: 'STREAMING',
      streamRunId: runId,
      streamedText: '',
      streamError: null,
      lastEventId: null
    }),

  appendDelta: (runId, text, eventId) => {
    const current = get();
    if (current.streamRunId !== runId) return;
    set({
      streamedText: current.streamedText + text,
      streamPhase: 'STREAMING',
      streamError: null,
      lastEventId: eventId ?? current.lastEventId
    });
  },

  markReconnecting: (runId) => {
    if (get().streamRunId !== runId) return;
    set({ streamPhase: 'RECONNECTING' });
  },

  setEventCursor: (eventId) => set({ lastEventId: eventId }),

  markFailed: (runId, message) => {
    if (get().streamRunId !== runId) return;
    set({ streamPhase: 'FAILED', streamError: message });
  },

  finishStream: (runId) => {
    if (get().streamRunId !== runId) return;
    set({ streamPhase: 'COMPLETED', streamedText: '', lastEventId: null });
  },

  resetStream: () =>
    set({
      streamPhase: 'IDLE',
      streamRunId: null,
      streamedText: '',
      streamError: null,
      lastEventId: null
    })
}));

/** 流是否仍在进行中（用于禁用输入框与展示「停止/重连」文案）。 */
export function isStreamActive(phase: AiStreamPhase): boolean {
  return phase === 'STREAMING' || phase === 'RECONNECTING';
}
