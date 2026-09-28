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

const SUGGESTIONS = ['查看我的钱包余额', '把 1 元转给 13800000000', '帮我看看最近的账单'];

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
              <h2 className={chatStyles.emptyTitle}>你好，我是米灵</h2>
              <p style={{ color: '#667085', fontSize: 13, lineHeight: 1.6 }}>
                我可以帮你查余额、看账单，并引导你完成转账。所有资金操作都会先由你确认，
                并跳转到钱包完成支付密码校验。
              </p>
              <div className={chatStyles.suggestions}>
                {SUGGESTIONS.map((text) => (
                  <button
                    key={text}
                    type="button"
                    className={chatStyles.suggestionChip}
                    onClick={() => void handleSuggestion(text)}
                  >
                    {text}
                  </button>
                ))}
              </div>
              <div className={chatStyles.suggestions}>
                <Button size="mini" color="primary" fill="outline" onClick={() => navigate(ROUTES.wallet)}>
                  去钱包转账
                </Button>
              </div>
            </section>
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
