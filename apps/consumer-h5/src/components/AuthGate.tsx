import { useLocation, useNavigate } from '@umijs/max';
import { useEffect } from 'react';
import type { PropsWithChildren } from 'react';
import { useSessionQuery } from '../hooks/useSession';
import { ROUTES } from '../constants/routes';
import { isAuthenticatedSession } from '../types/consumer';
import { withRedirect } from '../utils/redirect';
import { AsyncState } from './AsyncState';

/**
 * 鉴权门：只有服务端 `GET /api/v1/session` 返回 authenticated=true 才渲染业务页面。
 * 未登录时跳转登录页并带上回跳地址（只接受站内路径）；会话查询失败时展示可重试的错误态。
 */
export function AuthGate({ children }: PropsWithChildren) {
  const navigate = useNavigate();
  const location = useLocation();
  const query = useSessionQuery();
  const authenticatedSession = isAuthenticatedSession(query.data) ? query.data : undefined;
  const isAuthenticated = authenticatedSession !== undefined;
  const onboardingRequired = authenticatedSession?.onboardingRequired ?? false;

  useEffect(() => {
    if (query.isLoading || query.isError) return;
    if (isAuthenticated) {
      if (onboardingRequired && location.pathname !== ROUTES.onboarding) {
        navigate(ROUTES.onboarding, { replace: true });
      }
      return;
    }
    const current = `${location.pathname}${location.search ?? ''}`;
    navigate(withRedirect(ROUTES.login, current), { replace: true });
  }, [isAuthenticated, location.pathname, location.search, navigate, onboardingRequired, query.isError, query.isLoading]);

  if (query.isError) {
    return <AsyncState error={query.error} onRetry={() => void query.refetch()} />;
  }
  if (!isAuthenticated || (onboardingRequired && location.pathname !== ROUTES.onboarding)) {
    return <AsyncState loading loadingText="正在校验登录状态…" />;
  }
  return <>{children}</>;
}
