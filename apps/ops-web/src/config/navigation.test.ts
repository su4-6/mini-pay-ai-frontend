import { describe, expect, it } from 'vitest';
import {
  findOpsRouteByPath,
  getOpsBreadcrumbTitles,
  opsMenuGroups,
  opsRoutes,
  visibleOpsRoutes
} from './navigation';

describe('OPS navigation metadata', () => {
  it('contains only the approved initialization navigation in the expected order', () => {
    expect(opsRoutes.map((route) => route.key)).toEqual([
      'dashboard',
      'merchants',
      'review',
      'application-review',
      'applications',
      'payments',
      'refunds',
      'transfers',
      'recharges',
      'withdrawals',
      'food-orders',
      'collection-records',
      'notifications',
      'login-audits'
    ]);
    expect(opsMenuGroups.map((group) => group.key)).toEqual([
      'merchant-management',
      'order-management'
    ]);
  });

  it('keeps route keys and paths unique and uses notifications naming', () => {
    expect(new Set(opsRoutes.map((route) => route.key)).size).toBe(opsRoutes.length);
    expect(new Set(opsRoutes.map((route) => route.path)).size).toBe(opsRoutes.length);
    expect(findOpsRouteByPath('/system/audits/login/')?.key).toBe('login-audits');
    expect(findOpsRouteByPath('/notifications')?.key).toBe('notifications');
    expect(findOpsRouteByPath('/callbacks')).toBeUndefined();
  });

  it('marks every routed module with an implemented page as available', () => {
    const availability = Object.fromEntries(
      opsRoutes.map((route) => [route.key, route.availability])
    );
    expect(availability).toMatchObject({
      dashboard: 'available',
      merchants: 'available',
      review: 'available',
      'application-review': 'available',
      applications: 'available',
      payments: 'available',
      refunds: 'available',
      transfers: 'available',
      notifications: 'available',
      'login-audits': 'available'
    });
  });

  it('derives homepage-first breadcrumbs from the same route metadata', () => {
    expect(getOpsBreadcrumbTitles(opsRoutes[0])).toEqual(['主页']);
    expect(getOpsBreadcrumbTitles(
      opsRoutes.find((route) => route.key === 'merchants')!
    )).toEqual(['主页', '商户管理', '商户列表']);
  });

  it('shows authorized navigation but always hides the direct login audit route', () => {
    const routes = visibleOpsRoutes({
      roles: ['platform_admin'],
      permissions: [
        'ops.portal', 'ops.audit.read', 'ops.dashboard.read',
        'ops.merchant.read', 'ops.merchant.write', 'ops.application.read'
      ]
    });
    expect(routes.map((route) => route.key)).toEqual([
      'dashboard', 'merchants', 'review', 'application-review', 'applications',
      'payments', 'refunds', 'transfers', 'recharges', 'withdrawals', 'food-orders',
      'collection-records', 'notifications'
    ]);
  });

  it('does not expose OPS navigation to a non-platform role', () => {
    expect(visibleOpsRoutes({
      roles: ['merchant_admin'],
      permissions: ['ops.portal', 'ops.audit.read']
    })).toEqual([]);
  });
});
