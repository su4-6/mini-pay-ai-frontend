import { useNavigate } from '@umijs/max';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Dialog, Toast } from 'antd-mobile';
import { RightOutline } from 'antd-mobile-icons';
import { maskPhone } from '@minipay/shared';
import { AppShell } from '../components/AppShell';
import { AuthGate } from '../components/AuthGate';
import { Card } from '../components/Card';
import { InlineNotice } from '../components/InlineNotice';
import { ProblemNotice } from '../components/ProblemNotice';
import { ROUTES } from '../constants/routes';
import { useSession } from '../hooks/useSession';
import { clearCsrfToken } from '../services/http';
import { deleteSession } from '../services/session';
import { useChatStore } from '../stores/chat';
import { useTransferStore } from '../stores/transfer-intent';
import styles from './me.module.less';

const REAL_NAME_STATUS_TEXT: Record<string, string> = {
  UNVERIFIED: '未实名（本演示端不提供实名开户）',
  PENDING: '实名审核中（本演示端不提供实名开户）',
  VERIFIED: '已实名',
  REJECTED: '实名未通过'
};

function MeWorkspace() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { profile, payPasswordSet } = useSession();

  const logoutMutation = useMutation({
    mutationFn: deleteSession,
    onSuccess: () => {
      // 登出后会话与 CSRF 全部失效：清空内存态与查询缓存，绝不遗留敏感上下文。
      clearCsrfToken();
      useTransferStore.getState().clear();
      useChatStore.getState().resetStream();
      useChatStore.getState().setActiveConversation(null);
      queryClient.clear();
      Toast.show({ content: '已退出登录' });
      navigate(ROUTES.login, { replace: true });
    }
  });

  async function handleLogout(): Promise<void> {
    const confirmed = await Dialog.confirm({
      title: '退出登录',
      content: '退出后需要重新用短信验证码登录，本地不会保留支付密码或会话数据。',
      confirmText: '退出登录',
      cancelText: '取消'
    });
    if (confirmed) logoutMutation.mutate();
  }

  return (
    <AppShell title="我的" showTabBar>
      <section className={styles.header} aria-label="账户信息">
        <div className={styles.avatar} aria-hidden>
          {(profile?.displayName ?? 'M').slice(0, 1)}
        </div>
        <div>
          <div className={styles.name}>{profile?.displayName ?? 'MiniPay 用户'}</div>
          <div className={styles.meta}>{profile?.phone ? maskPhone(profile.phone) : '未获取到手机号'}</div>
        </div>
      </section>

      <Card title="账户与安全" tight>
        <button type="button" className={styles.entry} onClick={() => navigate(ROUTES.payPassword)}>
          <span>
            <span className={styles.entryLabel}>支付密码</span>
            <span className={styles.entryHint}>用于转账等资金操作的服务端校验</span>
          </span>
          <span className={styles.entryValue}>
            {payPasswordSet ? '已设置' : '未设置'}
            <RightOutline aria-hidden />
          </span>
        </button>

        <div className={styles.entry} aria-readonly="true">
          <span>
            <span className={styles.entryLabel}>实名状态</span>
            <span className={styles.entryHint}>演示环境不提供注册开户与实名认证</span>
          </span>
          <span className={styles.entryValue}>
            {REAL_NAME_STATUS_TEXT[profile?.realNameStatus ?? 'UNVERIFIED'] ?? profile?.realNameStatus ?? '--'}
          </span>
        </div>

        <div className={styles.entry} aria-readonly="true">
          <span>
            <span className={styles.entryLabel}>用户 ID</span>
            <span className={styles.entryHint}>服务端会话主体标识</span>
          </span>
          <span className={styles.entryValue} style={{ fontFamily: 'monospace' }}>
            {profile?.userId ? `${profile.userId.slice(0, 8)}…` : '--'}
          </span>
        </div>
      </Card>

      <Card title="常用入口" tight>
        <button type="button" className={styles.entry} onClick={() => navigate(ROUTES.wallet)}>
          <span className={styles.entryLabel}>钱包与账单</span>
          <RightOutline aria-hidden />
        </button>
        <button type="button" className={styles.entry} onClick={() => navigate(ROUTES.transfers)}>
          <span className={styles.entryLabel}>转账记录</span>
          <RightOutline aria-hidden />
        </button>
        <button type="button" className={styles.entry} onClick={() => navigate(ROUTES.collect)}>
          <span className={styles.entryLabel}>我的收款码</span>
          <RightOutline aria-hidden />
        </button>
      </Card>

      {logoutMutation.isError ? <ProblemNotice error={logoutMutation.error} /> : null}

      <Button block fill="outline" color="danger" loading={logoutMutation.isPending} onClick={() => void handleLogout()}>
        退出登录
      </Button>

      <div style={{ marginTop: 12 }}>
        <InlineNotice>
          当前为 Android App 下线后的 H5 演示端：不包含注册开户 / 实名、扫码、点餐外卖、好友与群聊、语音通话与后台推送。
        </InlineNotice>
      </div>
    </AppShell>
  );
}

export default function MePage() {
  return (
    <AuthGate>
      <MeWorkspace />
    </AuthGate>
  );
}
