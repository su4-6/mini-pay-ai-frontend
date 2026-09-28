import { SpinLoading } from 'antd-mobile';
import { useEffect, useRef } from 'react';
import type { AiMessage, AiStreamPhase, AiSuggestedAction } from '../../types/consumer';
import { formatFenWithSymbol } from '../../utils/money';
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
  onSuggestedAction?: (action: AiSuggestedAction) => void;
}

function maskedPayee(identifier?: string): string {
  if (!identifier) return '待确认收款人';
  return /^1[3-9]\d{9}$/.test(identifier)
    ? `${identifier.slice(0, 3)}****${identifier.slice(-4)}`
    : identifier;
}

function isTransferAction(action?: AiSuggestedAction): action is AiSuggestedAction & {
  amountFen: number;
  payeeIdentifier: string;
} {
  return action?.type === 'TRANSFER'
    && Number.isInteger(action.amountFen)
    && (action.amountFen ?? 0) > 0
    && Boolean(action.payeeIdentifier?.trim());
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
  onDismissError,
  onSuggestedAction
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
              {!mine ? <span className={styles.messageAvatar} aria-hidden>米</span> : null}
              <div className={`${styles.bubble} ${mine ? styles.bubbleUser : styles.bubbleAssistant}`}>
                <div className={styles.bubbleRole} style={mine ? { color: 'rgba(255,255,255,.75)' } : undefined}>
                  {roleLabel(message.role)}
                  {message.createdAt ? ` · ${formatDateTime(message.createdAt)}` : ''}
                </div>
                {message.content}
                {isTransferAction(message.suggestedAction) ? (
                  <section className={styles.actionCard} aria-label="转账建议">
                    <div>
                      <span className={styles.actionEyebrow}>待你确认</span>
                      <strong>{formatFenWithSymbol(message.suggestedAction.amountFen)}</strong>
                      <span>转给 {maskedPayee(message.suggestedAction.payeeIdentifier)}</span>
                    </div>
                    <button
                      type="button"
                      className={styles.actionButton}
                      onClick={() => onSuggestedAction?.(message.suggestedAction as AiSuggestedAction)}
                    >
                      核对并转账
                    </button>
                    <small>米灵不会直接扣款；下一页仍需服务端校验收款人并由你输入支付密码。</small>
                  </section>
                ) : null}
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
