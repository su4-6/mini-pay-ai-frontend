import { useCallback, useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { createRequestId } from '@minipay/shared';
import {
  AI_EVENT_TYPE,
  deltaText,
  errorText,
  isRunFailure,
  isRunTerminal,
  parseRunEvent,
  runEventsUrl,
  sendMessage
} from '../services/ai';
import { streamRunEvents } from '../services/sse';
import { describeProblem } from '../services/problem';
import { aiQueryKeys } from '../query/keys';
import { isStreamActive, useChatStore } from '../stores/chat';
import type { AiStreamPhase } from '../types/consumer';

export const MAX_PROMPT_LENGTH = 1_000;

export interface AiStreamController {
  phase: AiStreamPhase;
  streamedText: string;
  errorMessage: string | null;
  activeRunId: string | null;
  isSubmitting: boolean;
  send: (conversationId: string, content: string) => Promise<void>;
  stop: () => void;
  dismissError: () => void;
}

/**
 * 米灵流式回复控制器。
 *
 * 调用顺序与 Android `MilingAiViewModel.submit` / `observeRun` 对齐：
 *   1. POST /api/v1/ai/conversations/{id}/messages {content, clientMessageId} → {runId}
 *   2. SSE GET /api/v1/ai/runs/{runId}/events，带 Last-Event-ID 断点续传
 *   3. 收到 stream.completed 后按精确 query key 失效消息与会话列表
 *
 * 断线：非终态断开按退避重连（最多 3 次）；已收到明确失败事件时不再重连。
 * 结束：以服务端事件为准，前端不伪造成功。
 */
export function useAiStream(conversationId: string | null): AiStreamController {
  const queryClient = useQueryClient();
  const phase = useChatStore((state) => state.streamPhase);
  const streamedText = useChatStore((state) => state.streamedText);
  const errorMessage = useChatStore((state) => state.streamError);
  const activeRunId = useChatStore((state) => state.streamRunId);
  const streamConversationId = useChatStore((state) => state.activeConversationId);
  const lastEventId = useChatStore((state) => state.lastEventId);

  const abortRef = useRef<AbortController | null>(null);
  const failureRef = useRef<string | null>(null);
  const [isSubmitting, setSubmitting] = useState(false);

  const invalidateConversation = useCallback(
    (targetConversationId: string) => {
      void queryClient.invalidateQueries({ queryKey: aiQueryKeys.messages(targetConversationId) });
      void queryClient.invalidateQueries({ queryKey: aiQueryKeys.conversations });
    },
    [queryClient]
  );

  const runStream = useCallback(
    async (targetConversationId: string, runId: string, resumeFrom: string | null) => {
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;
      failureRef.current = null;
      try {
        await streamRunEvents({
          url: runEventsUrl(runId),
          signal: controller.signal,
          lastEventId: resumeFrom,
          onCursor: (eventId) => useChatStore.getState().setEventCursor(eventId),
          onReconnecting: () => useChatStore.getState().markReconnecting(runId),
          isTerminal: (frame) => {
            const event = parseRunEvent(frame);
            return event ? isRunTerminal(event) : false;
          },
          shouldReconnect: () => failureRef.current === null,
          onFrame: (frame) => {
            const event = parseRunEvent(frame);
            if (!event) return;
            if (isRunTerminal(event)) {
              useChatStore.getState().finishStream(runId);
              return;
            }
            if (isRunFailure(event)) {
              const message = errorText(event);
              failureRef.current = message;
              useChatStore.getState().markFailed(runId, message);
              return;
            }
            if (event.type === AI_EVENT_TYPE.DELTA) {
              const text = deltaText(event);
              if (text) useChatStore.getState().appendDelta(runId, text, event.id || null);
            }
          }
        });
      } catch (error) {
        if (!controller.signal.aborted) {
          const view = describeProblem(error);
          useChatStore.getState().markFailed(runId, view.message);
        }
      } finally {
        invalidateConversation(targetConversationId);
        if (abortRef.current === controller) abortRef.current = null;
      }
    },
    [invalidateConversation]
  );

  // 重新进入会话时续传未完成的运行（例如切页/刷新后仍在流式生成）。
  useEffect(() => {
    if (!conversationId) return;
    if (streamConversationId !== conversationId) return;
    if (!activeRunId) return;
    if (!isStreamActive(phase)) return;
    if (abortRef.current) return;
    void runStream(conversationId, activeRunId, lastEventId);
  }, [conversationId, streamConversationId, activeRunId, phase, lastEventId, runStream]);

  useEffect(
    () => () => {
      abortRef.current?.abort();
      abortRef.current = null;
    },
    []
  );

  const send = useCallback(
    async (targetConversationId: string, content: string) => {
      const trimmed = content.trim();
      if (!trimmed || trimmed.length > MAX_PROMPT_LENGTH) return;
      setSubmitting(true);
      try {
        const { runId } = await sendMessage(targetConversationId, trimmed, createRequestId());
        useChatStore.getState().beginStream(targetConversationId, runId);
        // 立刻对齐服务端消息，避免本地乐观消息与服务端事实重复。
        void queryClient.invalidateQueries({ queryKey: aiQueryKeys.messages(targetConversationId) });
        await runStream(targetConversationId, runId, null);
      } catch (error) {
        const view = describeProblem(error);
        useChatStore.setState({ streamPhase: 'FAILED', streamError: view.message });
      } finally {
        setSubmitting(false);
      }
    },
    [queryClient, runStream]
  );

  const stop = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    useChatStore.getState().resetStream();
  }, []);

  const dismissError = useCallback(() => {
    useChatStore.getState().resetStream();
  }, []);

  return { phase, streamedText, errorMessage, activeRunId, isSubmitting, send, stop, dismissError };
}
