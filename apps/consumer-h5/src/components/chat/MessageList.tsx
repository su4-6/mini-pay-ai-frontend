import { SpinLoading } from 'antd-mobile';
import { useEffect, useRef } from 'react';
import type { AiMessage, AiStreamPhase } from '../../types/consumer';
import { formatDateTime } from '../../utils/datetime';
import { InlineNotice } from '../InlineNotice';
import { ProblemNotice } from '../ProblemNotice';
import styles from './chat.module.less';

export interface MessageListProps {
  messages: AiMessage[];
  streamedText: string;
  phase: AiStreamPhase;
  streamError: string | null;
  /** 发送失败时的原始异常（保留 requestId）。 */
  sendError?: unknown;
  onDismissError?: () => void;
}

function roleLabel(role: AiMessage['role']): string {
  if (role === 'USER') return '我';
  if (role === 'SYSTEM') return '系统';
  if (role === 'TOOL') return '工具';
  return '米灵';
}

/** 对话消息区：服务端历史消息 + 正在流式生成的临时气泡。 */
export function MessageList({
  messages,
  streamedText,
  phase,
  streamError,
  sendError,
  onDismissError
}: MessageListProps) {
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const streaming = phase === 'STREAMING' || phase === 'RECONNECTING';

  useEffect(() => {
    sentinelRef.current?.scrollIntoView({ block: 'end', behavior: 'smooth' });
  }, [messages.length, streamedText, phase]);

  return (
    <div className={styles.messageList} role="log" aria-live="polite" aria-label="米灵对话消息">
      <ul className={styles.messageList}>
        {messages.map((message) => {
          const mine = message.role === 'USER';
          return (
            <li
              key={message.id}
              className={mine ? `${styles.bubbleRow} ${styles.bubbleRowUser}` : styles.bubbleRow}
            >
              <div className={`${styles.bubble} ${mine ? styles.bubbleUser : styles.bubbleAssistant}`}>
                <div className={styles.bubbleRole} style={mine ? { color: 'rgba(255,255,255,.75)' } : undefined}>
                  {roleLabel(message.role)}
                  {message.createdAt ? ` · ${formatDateTime(message.createdAt)}` : ''}
                </div>
                {message.content}
              </div>
            </li>
          );
        })}

        {streamedText || streaming ? (
          <li className={styles.bubbleRow}>
            <div className={`${styles.bubble} ${styles.bubbleAssistant}`}>
              <div className={styles.bubbleRole}>米灵</div>
              {streamedText}
              {streaming ? <span className={styles.streamingCursor} aria-hidden /> : null}
              {streaming ? (
                <div className={styles.streamStatus} role="status">
                  <SpinLoading style={{ '--size': '14px' }} color="primary" />
                  {phase === 'RECONNECTING' ? '连接中断，正在恢复…' : '正在生成…'}
                </div>
              ) : null}
            </div>
          </li>
        ) : null}
      </ul>

      {streamError ? <InlineNotice tone="danger">米灵回复失败：{streamError}</InlineNotice> : null}
      {sendError ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <ProblemNotice error={sendError} />
          {onDismissError ? (
            <button type="button" className={styles.suggestionChip} onClick={onDismissError}>
              知道了
            </button>
          ) : null}
        </div>
      ) : null}
      <div ref={sentinelRef} />
    </div>
  );
}
