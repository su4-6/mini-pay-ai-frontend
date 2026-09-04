import { Button, Card, SafeArea, Tag } from 'antd-mobile';
import { create } from 'zustand';
import styles from './index.module.less';

type AgentShellState = {
  authorizationPromptVisible: boolean;
  openAuthorizationPrompt: () => void;
  dismissAuthorizationPrompt: () => void;
};

const useAgentShellStore = create<AgentShellState>((set) => ({
  authorizationPromptVisible: false,
  openAuthorizationPrompt: () => set({ authorizationPromptVisible: true }),
  dismissAuthorizationPrompt: () => set({ authorizationPromptVisible: false })
}));

export default function ConsumerHomePage() {
  const { authorizationPromptVisible, openAuthorizationPrompt, dismissAuthorizationPrompt } =
    useAgentShellStore();

  return (
    <main className={styles.page}>
      <SafeArea position="top" />
      <header className={styles.header}>
        <Tag color="primary">米灵</Tag>
        <h1>MiniPay AI</h1>
        <p>消费者 H5 工程骨架</p>
      </header>
      <Card title="移动端安全边界">
        <p>Consumer H5 不内嵌到 Android。外卖 H5 的授权与付款将由原生 App 承载。</p>
      </Card>
      <Card title="授权交互占位" className={styles.card}>
        <p>未来在米灵对话中请求外卖授权；当前不加载外卖页面，也不会创建订单。</p>
        <Button color="primary" onClick={openAuthorizationPrompt}>
          查看授权卡占位
        </Button>
        {authorizationPromptVisible && (
          <div className={styles.authorizationCard} role="status">
            <strong>外卖项目请求授权</strong>
            <span>授权能力与账号绑定将在 Android 原生流程中完成。</span>
            <Button size="small" fill="none" onClick={dismissAuthorizationPrompt}>
              暂不
            </Button>
          </div>
        )}
      </Card>
      <SafeArea position="bottom" />
    </main>
  );
}
