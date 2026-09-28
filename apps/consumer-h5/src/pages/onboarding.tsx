import { useNavigate } from '@umijs/max';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Input, Toast } from 'antd-mobile';
import { useState } from 'react';
import { AppShell } from '../components/AppShell';
import { AuthGate } from '../components/AuthGate';
import { ProblemNotice } from '../components/ProblemNotice';
import { ROUTES } from '../constants/routes';
import { queryKeys } from '../query/keys';
import { completeOnboarding } from '../services/account';
import styles from './feature.module.less';

function OnboardingWorkspace() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [nickname, setNickname] = useState('');
  const mutation = useMutation({
    mutationFn: () => completeOnboarding(nickname.trim()),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.session });
      Toast.show({ icon: 'success', content: '欢迎加入 MiniPay' });
      navigate(ROUTES.home, { replace: true });
    }
  });
  const valid = /^[\p{L}\p{N}_]{2,20}$/u.test(nickname.trim());
  return <AppShell title="完善资料">
    <div className={styles.page}>
      <section className={styles.intro}><h1>先告诉我们怎么称呼你</h1><p>昵称会展示在收款码和米灵会话中，可以稍后在个人资料里修改。</p></section>
      <section className={styles.card}>
        <div className={styles.field}><label htmlFor="nickname">昵称</label><Input id="nickname" value={nickname} maxLength={20} placeholder="2–20 位中文、字母、数字或下划线" onChange={setNickname} /></div>
        {mutation.isError ? <ProblemNotice error={mutation.error} /> : null}
        <Button block color="primary" size="large" loading={mutation.isPending} disabled={!valid} onClick={() => mutation.mutate()}>进入 MiniPay</Button>
      </section>
    </div>
  </AppShell>;
}
export default function OnboardingPage(){return <AuthGate><OnboardingWorkspace /></AuthGate>}
