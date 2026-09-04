import { describe, expect, it } from 'vitest';
import type { SessionResponse } from '@minipay/api-contracts';
import { isSystemAdministrator, resolveSessionPhase } from './AuthGate';

const authenticated: SessionResponse = {
  authenticated: true,
  loginUrl: '/oauth2/authorization/minipay-ops',
  admin: {
    userId: 'admin-1',
    displayName: '演示管理员',
    roles: ['platform_admin'],
    permissions: ['ops.portal', 'ops.audit.read']
  }
};

describe('AuthGate session state', () => {
  it('keeps system administrators out of the operations portal', () => {
    expect(isSystemAdministrator(['system_super_admin'])).toBe(true);
    expect(isSystemAdministrator(['system_account_admin'])).toBe(true);
    expect(isSystemAdministrator(['system_auditor'])).toBe(true);
    expect(isSystemAdministrator(['platform_admin'])).toBe(false);
  });

  it('keeps pending requests in a stable loading state', () => {
    expect(resolveSessionPhase(true, false, undefined)).toBe('loading');
  });

  it('redirects unauthenticated responses without rendering an error frame', () => {
    expect(resolveSessionPhase(false, false, {
      authenticated: false,
      loginUrl: '/oauth2/authorization/minipay-ops',
      admin: null
    })).toBe('redirecting');
    expect(resolveSessionPhase(false, true, {
      authenticated: false,
      loginUrl: '/oauth2/authorization/minipay-ops',
      admin: null
    })).toBe('redirecting');
  });

  it('shows errors only after requests fail without usable session data', () => {
    expect(resolveSessionPhase(false, true, undefined)).toBe('error');
    expect(resolveSessionPhase(false, false, authenticated)).toBe('authenticated');
  });
});
