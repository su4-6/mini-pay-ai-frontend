import { useNavigate } from '@umijs/max';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Toast } from 'antd-mobile';
import { useState } from 'react';
import { AppShell } from '../components/AppShell';
import { AuthGate } from '../components/AuthGate';
import { Card } from '../components/Card';
import { InlineNotice } from '../components/InlineNotice';
import { PayPasswordField } from '../components/PayPasswordField';
import { ProblemNotice } from '../components/ProblemNotice';
import { ROUTES } from '../constants/routes';
import { useSession } from '../hooks/useSession';
import { queryKeys } from '../query/keys';
import { setPayPassword } from '../services/session';
import { isPayPassword } from '../utils/validators';
import styles from './pay-password.module.less';

function PayPasswordWorkspace() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { payPasswordSet } = useSession();

  // 只在组件内保存，提交成功后立即清空；不写 localStorage、不进 URL、不进日志。
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [formError, setFormError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: setPayPassword,
    onSuccess: () => {
      setPassword('');
      setConfirmation('');
      setFormError(null);
      Toast.show({ content: '支付密码设置成功' });
      // 支付密码状态属于会话声明，精确失效会话查询后回跳。
      void queryClient.invalidateQueries({ queryKey: queryKeys.session });
      navigate(ROUTES.transfer, { replace: true });
    },
    onError: () => {
      setPassword('');
      setConfirmation('');
    }
  });

  function handleSubmit(): void {
    if (!isPayPassword(password)) {
      setFormError('支付密码必须是 6 位数字');
      return;
    }
    if (password !== confirmation) {
      setFormError('两次输入的支付密码不一致');
      return;
    }
    if (/^(\d)\1{5}$/.test(password) || password === '123456') {
      setFormError('支付密码过于简单，请更换为更复杂的 6 位数字');
      return;
    }
    setFormError(null);
    mutation.mutate(password);
  }

  return (
    <AppShell
      title="设置支付密码"
      subtitle="支付密码用于转账等资金操作的服务端二次校验"
      backTo="/me"
    >
      <div className={styles.page}>
        {payPasswordSet ? (
          <InlineNotice tone="warning">
            当前账户已设置支付密码。重复设置可能被服务端拒绝，如需修改请前往账户安全。
          </InlineNotice>
        ) : null}

        <Card>
          <div className={styles.field}>
            <PayPasswordField
              label="支付密码"
              value={password}
              onChange={(value) => {
                setPassword(value);
                setFormError(null);
              }}
              disabled={mutation.isPending}
              autoFocus
            />
          </div>
          <div className={styles.field} style={{ marginTop: 12 }}>
            <PayPasswordField
              label="确认支付密码"
              value={confirmation}
              onChange={(value) => {
                setConfirmation(value);
                setFormError(null);
              }}
              disabled={mutation.isPending}
            />
          </div>

          {formError ? <InlineNotice tone="warning">{formError}</InlineNotice> : null}
          {mutation.isError ? <ProblemNotice error={mutation.error} /> : null}

          <div style={{ marginTop: 12 }}>
            <Button
              block
              color="primary"
              size="large"
              loading={mutation.isPending}
              disabled={!isPayPassword(password) || !isPayPassword(confirmation)}
              onClick={handleSubmit}
            >
              确认设置
            </Button>
          </div>
        </Card>

        <Card title="安全说明" tight>
          <ul className={styles.rules}>
            <li>支付密码只提交到服务端做一次校验或写入，浏览器不保存明文。</li>
            <li>不会写入 localStorage / sessionStorage，也不会出现在跳转地址或日志中。</li>
            <li>提交后输入框立即清空，页面离开即丢失。</li>
            <li>请勿使用连续或重复数字等易被猜测的组合。</li>
          </ul>
        </Card>
      </div>
    </AppShell>
  );
}

export default function PayPasswordPage() {
  return (
    <AuthGate>
      <PayPasswordWorkspace />
    </AuthGate>
  );
}
