import { useNavigate } from '@umijs/max';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Toast } from 'antd-mobile';
import { UnorderedListOutline } from 'antd-mobile-icons';
import { useState } from 'react';
import { AppShell } from '../components/AppShell';
import { AuthGate } from '../components/AuthGate';
import { AsyncState } from '../components/AsyncState';
import { ChatComposer } from '../components/chat/ChatComposer';
import { ConversationDrawer } from '../components/chat/ConversationDrawer';
import { MessageList } from '../components/chat/MessageList';
import chatStyles from '../components/chat/chat.module.less';
import { ROUTES } from '../constants/routes';
import { MAX_PROMPT_LENGTH, useAiStream } from '../hooks/useAiStream';
import { aiQueryKeys } from '../query/keys';
import { createConversation, listConversations, listMessages } from '../services/ai';
import { useChatStore } from '../stores/chat';
import { useTransferStore } from '../stores/transfer-intent';

const SUGGESTIONS = [
  { icon: '¥', title: '查询余额', prompt: '查看我的钱包余额', hint: '可用与冻结金额' },
  { icon: '↗', title: '转账助手', prompt: '把 1 元转给 13800000000', hint: '生成待确认转账' },
  { icon: '≡', title: '分析账单', prompt: '帮我看看最近的账单', hint: '查找最近收支' }
] as const;

function ChatWorkspace() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [sendError, setSendError] = useState<unknown>(null);

  const activeConversationId = useChatStore((state) => state.activeConversationId);
  const draft = useChatStore((state) => state.draft);
  const setDraft = useChatStore((state) => state.setDraft);
  const setActiveConversation = useChatStore((state) => state.setActiveConversation);
  const setTransferDraft = useTransferStore((state) => state.setDraft);

  const conversationsQuery = useQuery({
    queryKey: aiQueryKeys.conversations,
    queryFn: listConversations
  });

  const messagesQuery = useQuery({
    queryKey: aiQueryKeys.messages(activeConversationId ?? 'none'),
    queryFn: () => listMessages(activeConversationId as string),
    enabled: Boolean(activeConversationId)
  });

  const stream = useAiStream(activeConversationId);
  const streaming = stream.phase === 'STREAMING' || stream.phase === 'RECONNECTING';

  const createMutation = useMutation({
    mutationFn: () => createConversation(),
    onSuccess: (conversation) => {
      setActiveConversation(conversation.id);
      queryClient.setQueryData(aiQueryKeys.messages(conversation.id), { items: [] });
      void queryClient.invalidateQueries({ queryKey: aiQueryKeys.conversations });
    },
    onError: (error) => setSendError(error)
  });

  const conversations = conversationsQuery.data?.items ?? [];
  const messages = messagesQuery.data?.items ?? [];

  async function ensureConversationId(): Promise<string | null> {
    if (activeConversationId) return activeConversationId;
    try {
      const conversation = await createMutation.mutateAsync();
      return conversation.id;
    } catch {
      return null;
    }
  }

  async function handleSend(): Promise<void> {
    const content = draft.trim();
    if (!content) return;
    setSendError(null);
    const conversationId = await ensureConversationId();
    if (!conversationId) {
      Toast.show({ content: '会话创建失败，请重试' });
      return;
    }
    setDraft('');
    try {
      await stream.send(conversationId, content);
    } catch (error) {
      setSendError(error);
    }
  }

  async function handleSuggestion(text: string): Promise<void> {
    setSendError(null);
    const conversationId = await ensureConversationId();
    if (!conversationId) {
      Toast.show({ content: '会话创建失败，请重试' });
      return;
    }
    await stream.send(conversationId, text);
  }

  const showEmptyHero = !activeConversationId && messages.length === 0 && !stream.streamedText;

  return (
    <>
      <AppShell
        title="米灵"
        subtitle="AI 助手：钱包问答、账单查询与转账引导（资金操作仍需你在钱包页确认）"
        showTabBar
        flush
        right={
          <Button
            size="mini"
            fill="none"
            onClick={() => setDrawerVisible(true)}
            aria-label="打开会话列表"
          >
            <UnorderedListOutline fontSize={20} aria-hidden />
          </Button>
        }
      >
        <div className={chatStyles.workspace}>
          {showEmptyHero ? (
            <section className={chatStyles.emptyHero} aria-label="米灵引导">
              <div className={chatStyles.heroHeading}>
                <div className={chatStyles.assistantAvatar} aria-hidden>米</div>
                <div>
                  <span className={chatStyles.heroBadge}>AI 钱包助理 · 在线</span>
                  <h2 className={chatStyles.emptyTitle}>你好，我是米灵</h2>
                  <p className={chatStyles.heroDescription}>
                    我能帮你查询钱包、整理账单，也能把转账需求变成一张由你核对的确认卡。
                  </p>
                </div>
              </div>
              <div className={chatStyles.capabilityGrid}>
                {SUGGESTIONS.map((item) => (
                  <button
                    key={item.prompt}
                    type="button"
                    className={chatStyles.capabilityCard}
                    onClick={() => void handleSuggestion(item.prompt)}
                  >
                    <span className={chatStyles.capabilityIcon} aria-hidden>{item.icon}</span>
                    <span><strong>{item.title}</strong><small>{item.hint}</small></span>
                  </button>
                ))}
              </div>
              <div className={chatStyles.safetyNote}>
                <span aria-hidden>✓</span>
                米灵只读取获授权的沙箱数据，不接收支付密码，也不会直接扣款
              </div>
            </section>
          ) : null}

          {!showEmptyHero ? (
            <div className={chatStyles.assistantStatus} role="status">
              <span className={chatStyles.statusDot} aria-hidden />
              米灵已连接 · 资金操作仍由你最终确认
            </div>
          ) : null}

          <AsyncState
            loading={Boolean(activeConversationId) && messagesQuery.isLoading}
            error={messagesQuery.isError ? messagesQuery.error : undefined}
            onRetry={() => void messagesQuery.refetch()}
            loadingText="正在加载历史消息…"
          >
            <MessageList
              messages={messages}
              streamedText={stream.streamedText}
              phase={stream.phase}
              streamError={stream.errorMessage}
              sendError={sendError}
              onDismissError={() => {
                setSendError(null);
                stream.dismissError();
              }}
              onSuggestedAction={(action) => {
                if (!action.payeeIdentifier || !action.amountFen) return;
                setTransferDraft({
                  payeeIdentifier: action.payeeIdentifier,
                  amountFen: action.amountFen
                });
                navigate(ROUTES.transfer);
              }}
            />
          </AsyncState>

          {conversationsQuery.isError && conversations.length === 0 ? (
            <AsyncState error={conversationsQuery.error} onRetry={() => void conversationsQuery.refetch()} />
          ) : null}

          <ChatComposer
            value={draft}
            onChange={setDraft}
            onSend={() => void handleSend()}
            onStop={stream.stop}
            streaming={streaming}
            disabled={stream.isSubmitting}
            maxLength={MAX_PROMPT_LENGTH}
          />
        </div>
      </AppShell>

      <ConversationDrawer
        visible={drawerVisible}
        conversations={conversations}
        activeConversationId={activeConversationId}
        loading={conversationsQuery.isLoading}
        error={conversationsQuery.isError ? conversationsQuery.error : undefined}
        creating={createMutation.isPending}
        onClose={() => setDrawerVisible(false)}
        onSelect={(conversationId) => {
          setActiveConversation(conversationId);
          setDrawerVisible(false);
        }}
        onCreate={() => {
          createMutation.mutate();
          setDrawerVisible(false);
        }}
        onRetry={() => void conversationsQuery.refetch()}
      />
    </>
  );
}

export default function ChatPage() {
  return (
    <AuthGate>
      <ChatWorkspace />
    </AuthGate>
  );
}
