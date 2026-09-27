import { Button } from 'antd-mobile';
import { SendOutline, StopOutline } from 'antd-mobile-icons';
import type { KeyboardEvent } from 'react';
import styles from './chat.module.less';

export interface ChatComposerProps {
  value: string;
  onChange: (value: string) => void;
  onSend: () => void;
  onStop: () => void;
  streaming: boolean;
  disabled?: boolean;
  maxLength: number;
  placeholder?: string;
}

/**
 * 对话输入区。回车发送、Shift+Enter 换行；
 * 流式生成中禁用发送并提供「停止」，避免并发提交同一会话。
 */
export function ChatComposer({
  value,
  onChange,
  onSend,
  onStop,
  streaming,
  disabled,
  maxLength,
  placeholder = '和米灵说点什么，例如「给 13800000000 转 1 元」'
}: ChatComposerProps) {
  const canSend = value.trim().length > 0 && !streaming && !disabled;

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>): void {
    if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing) return;
    event.preventDefault();
    if (canSend) onSend();
  }

  return (
    <div className={styles.composer}>
      <label className="mp-visually-hidden" htmlFor="chat-composer">
        输入发给米灵的消息
      </label>
      <div className={styles.composerRow}>
        <textarea
          id="chat-composer"
          className={styles.textarea}
          value={value}
          maxLength={maxLength}
          disabled={disabled || streaming}
          placeholder={placeholder}
          onChange={(event) => onChange(event.target.value)}
          onKeyDown={handleKeyDown}
          rows={1}
        />
        <div className={styles.composerActions}>
          {streaming ? (
            <Button color="default" fill="outline" onClick={onStop} style={{ minHeight: 44 }}>
              <StopOutline aria-hidden /> 停止
            </Button>
          ) : (
            <Button color="primary" disabled={!canSend} onClick={onSend} style={{ minHeight: 44 }}>
              <SendOutline aria-hidden /> 发送
            </Button>
          )}
        </div>
      </div>
      <div className={styles.composerHint}>
        {value.length}/{maxLength} · Enter 发送，Shift + Enter 换行
      </div>
    </div>
  );
}
