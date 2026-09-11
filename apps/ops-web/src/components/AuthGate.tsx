import { useEffect, useState, type ReactNode } from 'react';
import type { SessionResponse } from '@minipay/api-contracts';
import { Alert, Button, Result } from 'antd';
import { useNavigate } from '@umijs/max';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getSession } from '../services/auth';
import { OpsShell } from './OpsShell';
import {
  findOpsRouteByKey,
  hasOpsRouteAccess,
  type OpsRouteKey
} from '../config/navigation';
import styles from './AuthGate.module.less';

interface AuthGateProps {
  routeKey: OpsRouteKey;
  children: ReactNode;
}

export type SessionPhase = 'loading' | 'redirecting' | 'error' | 'authenticated';

/**
 * 系统管理员被挡在运营端之外、需要跳去管理端时使用的地址。
 *
 * 不能直接用构建期注入的 ADMIN_WEB_PUBLIC_URL：`.umirc.ts` 在未传环境变量时
 * 会把它烤成 http://localhost:8002/，于是部署后系统管理员会被跳到构建机的
 * localhost（ERR_CONNECTION_REFUSED）。
 *
 * 因此：只有当注入值是一个不含 localhost 的绝对地址时才采用它；
 * 否则按当前域名推导（ops.example.com -> admin.example.com，
 * ops.minipay.localhost:18080 -> admin.minipay.localhost:18080），本地与线上自适应。
 */
export function resolveAdminPortalUrl(): string {
  const injected =
    typeof ADMIN_WEB_PUBLIC_URL === 'string' ? ADMIN_WEB_PUBLIC_URL : '';
  if (/^https?:\/\//.test(injected) && !injected.includes('localhost')) {
    return injected;
  }
  const { protocol, host } = window.location;
  // 只替换最前面的一段主机名，保留其余域名与端口。
  const adminHost = host.includes('.') ? host.replace(/^[^.]*/, 'admin') : host;
  return `${protocol}//${adminHost}/`;
}

export function resolveSessionPhase(
  isPending: boolean,
  isError: boolean,
  data: SessionResponse | undefined
): SessionPhase {
  if (isPending || (!data && !isError)) {
    return 'loading';
  }
  if (data && !data.authenticated) {
    return 'redirecting';
  }
  if (isError || !data?.admin) {
    return 'error';
  }
  return 'authenticated';
}

export function isSystemAdministrator(roles: string[] | undefined): boolean {
  return Boolean(roles?.some((role) =>
    ['system_super_admin', 'system_account_admin', 'system_auditor'].includes(role)
  ));
}

export function AuthGate({ routeKey, children }: AuthGateProps) {
  const navigate = useNavigate();
  const [showLoadingDetails, setShowLoadingDetails] = useState(false);
  const queryClient = useQueryClient();
  const route = findOpsRouteByKey(routeKey);
  const session = useQuery({
    queryKey: ['session'],
    queryFn: getSession,
    staleTime: 30_000,
    retry: 1,
    retryDelay: 300
  });
  const phase = resolveSessionPhase(session.isPending, session.isError, session.data);

  useEffect(() => {
    if (phase !== 'loading') {
      setShowLoadingDetails(false);
      return;
    }
    const timer = window.setTimeout(() => setShowLoadingDetails(true), 250);
    return () => window.clearTimeout(timer);
  }, [phase]);

  useEffect(() => {
    if (phase === 'redirecting' && session.data) {
      window.location.replace(session.data.loginUrl);
    }
  }, [phase, session.data]);

  const admin = session.data?.admin;
  const shouldUseAdminPortal = isSystemAdministrator(admin?.roles);

  useEffect(() => {
    if (shouldUseAdminPortal) {
      window.location.replace(resolveAdminPortalUrl());
    }
  }, [shouldUseAdminPortal]);

  const isForbidden = Boolean(
    admin &&
      !hasOpsRouteAccess(
        { roles: admin.roles, permissions: admin.permissions ?? [] },
        route
      )
  );

  useEffect(() => {
    if (!isForbidden) {
      return;
    }
    queryClient.removeQueries({
      predicate: (query) => query.queryKey[0] !== 'session'
    });
  }, [isForbidden, queryClient]);

  if (phase === 'loading' || phase === 'redirecting' || shouldUseAdminPortal) {
    return (
      <main className={styles.state} aria-busy="true" aria-label="正在验证登录状态">
        {showLoadingDetails && phase === 'loading' ? (
          <div className={styles.loadingCard} aria-hidden="true">
            <span className={styles.loadingLogo} />
            <span className={styles.loadingLine} />
            <span className={styles.loadingLineShort} />
          </div>
        ) : null}
        <span className={styles.visuallyHidden}>
          {phase === 'redirecting' ? '正在前往登录页面…' : '正在验证登录状态…'}
        </span>
      </main>
    );
  }

  if (phase === 'error' || !session.data?.admin) {
    return (
      <main className={styles.state}>
        <Alert
          showIcon
          type="error"
          title="无法验证登录状态"
          description="请检查网络连接后重试。"
        />
        <Button type="primary" onClick={() => void session.refetch()}>重新加载</Button>
      </main>
    );
  }

  if (isForbidden) {
    return (
      <OpsShell admin={session.data.admin} activeKey={routeKey}>
        <Result
          status="403"
          title="无权访问此页面"
          subTitle={`当前账号缺少 ${route.title} 所需权限。`}
          extra={<Button type="primary" onClick={() => navigate('/')}>返回工作台</Button>}
        />
      </OpsShell>
    );
  }

  return <OpsShell admin={session.data.admin} activeKey={routeKey}>{children}</OpsShell>;
}
