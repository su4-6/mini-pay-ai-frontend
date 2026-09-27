import { Button, Popup, SpinLoading } from 'antd-mobile';
import { AddOutline, CloseOutline } from 'antd-mobile-icons';
import type { AiConversation } from '../../types/consumer';
import { formatRelative } from '../../utils/datetime';
import { AsyncState } from '../AsyncState';
import styles from './chat.module.less';

export interface ConversationDrawerProps {
  visible: boolean;
  conversations: AiConversation[];
  activeConversationId: string | null;
  loading: boolean;
  error: unknown;
  creating: boolean;
  onClose: () => void;
  onSelect: (conversationId: string) => void;
  onCreate: () => void;
  onRetry: () => void;
}

/** 会话列表抽屉：历史会话切换 + 新建会话。 */
export function ConversationDrawer({
  visible,
  conversations,
  activeConversationId,
  loading,
  error,
  creating,
  onClose,
  onSelect,
  onCreate,
  onRetry
}: ConversationDrawerProps) {
  return (
    <Popup
      visible={visible}
      position="left"
      onMaskClick={onClose}
      bodyStyle={{ width: 'auto', background: 'transparent' }}
    >
      <div className={styles.drawer} role="dialog" aria-label="会话列表">
        <div className={styles.drawerHeader}>
          <span className={styles.drawerTitle}>会话列表</span>
          <div style={{ display: 'flex', gap: 8 }}>
            <Button size="mini" color="primary" fill="outline" loading={creating} onClick={onCreate}>
              <AddOutline aria-hidden /> 新建
            </Button>
            <Button size="mini" fill="none" onClick={onClose} aria-label="关闭会话列表">
              <CloseOutline aria-hidden />
            </Button>
          </div>
        </div>
        <div className={styles.drawerBody}>
          {loading && conversations.length === 0 ? (
            <div style={{ padding: 16, textAlign: 'center' }}>
              <SpinLoading color="primary" />
            </div>
          ) : null}
          {error && conversations.length === 0 ? (
            <div style={{ padding: 12 }}>
              <AsyncState error={error} onRetry={onRetry} />
            </div>
          ) : null}
          {!loading && !error && conversations.length === 0 ? (
            <p style={{ padding: 16, color: '#667085', fontSize: 13 }}>
              还没有历史会话，点击「新建」开始和米灵对话。
            </p>
          ) : null}
          <ul>
            {conversations.map((conversation) => {
              const active = conversation.id === activeConversationId;
              return (
                <li key={conversation.id}>
                  <button
                    type="button"
                    className={
                      active
                        ? `${styles.conversationItem} ${styles.conversationItemActive}`
                        : styles.conversationItem
                    }
                    onClick={() => onSelect(conversation.id)}
                    aria-current={active ? 'true' : undefined}
                  >
                    <span className={styles.conversationTitle}>{conversation.title || '未命名会话'}</span>
                    <span className={styles.conversationMeta}>
                      {formatRelative(conversation.lastMessageAt ?? conversation.updatedAt)}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        </div>
      </div>
    </Popup>
  );
}
