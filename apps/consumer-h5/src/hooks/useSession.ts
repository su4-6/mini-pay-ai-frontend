import { useQuery } from '@tanstack/react-query';
import { fetchSession } from '../services/session';
import { queryKeys } from '../query/keys';
import { isAuthenticatedSession, type ConsumerSessionProfile } from '../types/consumer';

/** 服务端会话状态（唯一权威来源）。 */
export function useSessionQuery() {
  return useQuery({
    queryKey: queryKeys.session,
    queryFn: fetchSession,
    staleTime: 30_000,
    retry: 1
  });
}

export interface SessionView {
  profile: ConsumerSessionProfile | undefined;
  isAuthenticated: boolean;
  isLoading: boolean;
  isError: boolean;
  payPasswordSet: boolean;
}

export function useSession(): SessionView {
  const query = useSessionQuery();
  const profile = isAuthenticatedSession(query.data) ? query.data : undefined;
  return {
    profile,
    isAuthenticated: Boolean(profile),
    isLoading: query.isLoading,
    isError: query.isError,
    payPasswordSet: profile?.payPasswordSet ?? false
  };
}
