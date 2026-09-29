import { useNavigate } from '@umijs/max';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Dialog, Toast } from 'antd-mobile';
import {
  BankcardOutline,
  BillOutline,
  PayCircleOutline,
  ReceivePaymentOutline,
  RightOutline,
  UserOutline
} from 'antd-mobile-icons';
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
  UNVERIFIED: '未实名',
  PENDING: '实名审核中',
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
        <div className={styles.identity}>
          <span className={styles.accountTag}>MINIPAY 账户</span>
          <div className={styles.name}>{profile?.displayName ?? 'MiniPay 用户'}</div>
          <div className={styles.meta}>{profile?.phone ? maskPhone(profile.phone) : '未获取到手机号'}</div>
        </div>
        <div className={styles.headerArrow}><RightOutline /></div>
      </section>

      <section className={styles.statusGrid} aria-label="账户状态概览">
        <div><span>实名认证</span><strong>{profile?.realNameVerified ? '已完成' : '待完善'}</strong></div>
        <div><span>支付密码</span><strong>{payPasswordSet ? '已开启' : '待设置'}</strong></div>
        <div><span>会话保护</span><strong>已启用</strong></div>
      </section>

      <Card title="账户与安全" tight>
        <button type="button" className={styles.entry} onClick={() => navigate(ROUTES.profile)}>
          <span className={`${styles.entryIcon} ${styles.iconBlue}`}><UserOutline /></span>
          <span className={styles.entryMain}>
            <span className={styles.entryLabel}>个人资料</span>
            <span className={styles.entryHint}>修改昵称与查看账户标识</span>
          </span>
          <RightOutline aria-hidden />
        </button>

        <button type="button" className={styles.entry} onClick={() => navigate(ROUTES.security)}>
          <span className={`${styles.entryIcon} ${styles.iconViolet}`}>安</span>
          <span className={styles.entryMain}>
            <span className={styles.entryLabel}>账户安全</span>
            <span className={styles.entryHint}>手机号与支付密码管理</span>
          </span>
          <span className={styles.entryValue}>
            {payPasswordSet ? '已设置' : '未设置'}
            <RightOutline aria-hidden />
          </span>
        </button>

        <button type="button" className={styles.entry} onClick={() => navigate(ROUTES.realName)}>
          <span className={`${styles.entryIcon} ${styles.iconGreen}`}>证</span>
          <span className={styles.entryMain}>
            <span className={styles.entryLabel}>实名状态</span>
            <span className={styles.entryHint}>资金操作前需要完成沙箱实名认证</span>
          </span>
          <span className={styles.entryValue}>
            {REAL_NAME_STATUS_TEXT[profile?.realNameStatus ?? 'UNVERIFIED'] ?? profile?.realNameStatus ?? '--'}
            <RightOutline aria-hidden />
          </span>
        </button>

        <div className={styles.entry} aria-readonly="true">
          <span className={`${styles.entryIcon} ${styles.iconGray}`}>ID</span>
          <span className={styles.entryMain}>
            <span className={styles.entryLabel}>用户 ID</span>
            <span className={styles.entryHint}>服务端会话主体标识</span>
          </span>
          <span className={styles.entryValue} style={{ fontFamily: 'monospace' }}>
            {profile?.userId ? `${profile.userId.slice(0, 8)}…` : '--'}
          </span>
        </div>
      </Card>

      <Card title="常用入口" tight>
        <button type="button" className={styles.entry} onClick={() => navigate(ROUTES.merchant)}>
          <span className={`${styles.entryIcon} ${styles.iconBlue}`}>商</span>
          <span className={styles.entryMain}>
            <span className={styles.entryLabel}>我的商户</span>
            <span className={styles.entryHint}>申请入驻、查看审核与开通商户收款码</span>
          </span>
          <RightOutline aria-hidden />
        </button>
        <button type="button" className={styles.entry} onClick={() => navigate(ROUTES.bankCards)}>
          <span className={`${styles.entryIcon} ${styles.iconBlue}`}><BankcardOutline /></span>
          <span className={styles.entryLabel}>我的银行卡</span>
          <RightOutline aria-hidden />
        </button>
        <button type="button" className={styles.entry} onClick={() => navigate(ROUTES.funding)}>
          <span className={`${styles.entryIcon} ${styles.iconGreen}`}><PayCircleOutline /></span>
          <span className={styles.entryLabel}>充值与提现</span>
          <RightOutline aria-hidden />
        </button>
        <button type="button" className={styles.entry} onClick={() => navigate(ROUTES.wallet)}>
          <span className={`${styles.entryIcon} ${styles.iconViolet}`}><BillOutline /></span>
          <span className={styles.entryLabel}>钱包与账单</span>
          <RightOutline aria-hidden />
        </button>
        <button type="button" className={styles.entry} onClick={() => navigate(ROUTES.transfers)}>
          <span className={`${styles.entryIcon} ${styles.iconOrange}`}><PayCircleOutline /></span>
          <span className={styles.entryLabel}>转账记录</span>
          <RightOutline aria-hidden />
        </button>
        <button type="button" className={styles.entry} onClick={() => navigate(ROUTES.collect)}>
          <span className={`${styles.entryIcon} ${styles.iconCyan}`}><ReceivePaymentOutline /></span>
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
          当前为沙箱 H5：支持钱包核心流程与沙箱实名，不连接真实银行；外卖、社交、群聊、语音和后台推送不在本期范围。
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
