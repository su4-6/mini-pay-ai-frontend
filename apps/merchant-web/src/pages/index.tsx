import { useQuery } from '@tanstack/react-query';
import { useNavigate } from '@umijs/max';
import { Spin } from 'antd';
import { useEffect } from 'react';
import { merchantApi } from '../services/merchant';
import styles from './index.module.less';

export default function MerchantEntryPage() {
  const session = useQuery({ queryKey: ['merchant-session'], queryFn: merchantApi.session, retry: false });
  const navigate = useNavigate();
  useEffect(() => {
    if (!session.isLoading) {
      navigate(session.data?.authenticated ? '/dashboard' : '/login', { replace: true });
    }
  }, [session.isLoading, session.data?.authenticated]);
  if (session.isLoading) return <div className={styles.centered}><Spin size="large" /></div>;
  return <div className={styles.centered}><Spin size="large" /></div>;
}
