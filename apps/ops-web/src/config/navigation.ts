import type { AuthenticatedAdmin } from '@minipay/api-contracts';

export type OpsPermission =
  | 'ops.portal'
  | 'ops.audit.read'
  | 'ops.dashboard.read'
  | 'ops.merchant.read'
  | 'ops.merchant.write'
  | 'ops.application.read'
  | 'ops.application.write';

export type OpsRouteKey =
  | 'dashboard'
  | 'merchants'
  | 'review'
  | 'application-review'
  | 'applications'
  | 'payments'
  | 'refunds'
  | 'transfers'
  | 'recharges'
  | 'withdrawals'
  | 'food-orders'
  | 'collection-records'
  | 'notifications'
  | 'login-audits';

export type OpsMenuGroupKey = 'merchant-management' | 'order-management';
export type OpsIconName = 'dashboard' | 'merchant' | 'order';

export interface OpsRouteMeta {
  key: OpsRouteKey;
  path: string;
  pageId: string;
  title: string;
  requiredPermissions: OpsPermission[];
  groupKey?: OpsMenuGroupKey;
  availability: 'available' | 'planned';
  navigationVisible: boolean;
}

export interface OpsMenuGroup {
  key: OpsMenuGroupKey;
  title: string;
  icon: OpsIconName;
}

const portalPermission: OpsPermission[] = ['ops.portal'];
const auditPermissions: OpsPermission[] = ['ops.portal', 'ops.audit.read'];
const dashboardPermissions: OpsPermission[] = ['ops.portal', 'ops.dashboard.read'];
const merchantPermissions: OpsPermission[] = ['ops.portal', 'ops.merchant.read'];
const applicationPermissions: OpsPermission[] = ['ops.portal', 'ops.application.read'];

export const opsMenuGroups: OpsMenuGroup[] = [
  { key: 'merchant-management', title: '商户管理', icon: 'merchant' },
  { key: 'order-management', title: '订单管理', icon: 'order' }
];

export const opsRoutes: OpsRouteMeta[] = [
  {
    key: 'dashboard',
    path: '/',
    pageId: 'OPS-02',
    title: '主页',
    requiredPermissions: dashboardPermissions,
    availability: 'available',
    navigationVisible: true
  },
  {
    key: 'merchants',
    path: '/merchants',
    pageId: 'OPS-03',
    title: '商户列表',
    requiredPermissions: merchantPermissions,
    groupKey: 'merchant-management',
    availability: 'available',
    navigationVisible: true
  },
  {
    key: 'review',
    path: '/review',
    pageId: 'OPS-13',
    title: '入驻审核',
    requiredPermissions: merchantPermissions,
    groupKey: 'merchant-management',
    availability: 'available',
    navigationVisible: true
  },
  {
    key: 'application-review',
    path: '/application-review',
    pageId: 'OPS-14',
    title: '应用审核',
    requiredPermissions: applicationPermissions,
    groupKey: 'merchant-management',
    availability: 'available',
    navigationVisible: true
  },
  {
    key: 'applications',
    path: '/applications',
    pageId: 'OPS-04',
    title: '应用列表',
    requiredPermissions: applicationPermissions,
    groupKey: 'merchant-management',
    availability: 'available',
    navigationVisible: true
  },
  {
    key: 'payments',
    path: '/payments',
    pageId: 'OPS-05',
    title: '支付订单',
    requiredPermissions: portalPermission,
    groupKey: 'order-management',
    availability: 'available',
    navigationVisible: true
  },
  {
    key: 'refunds',
    path: '/refunds',
    pageId: 'OPS-06',
    title: '退款订单',
    requiredPermissions: portalPermission,
    groupKey: 'order-management',
    availability: 'available',
    navigationVisible: true
  },
  {
    key: 'transfers',
    path: '/transfers',
    pageId: 'OPS-07',
    title: '转账订单',
    requiredPermissions: portalPermission,
    groupKey: 'order-management',
    availability: 'available',
    navigationVisible: true
  },
  {
    key: 'recharges',
    path: '/recharges',
    pageId: 'OPS-15',
    title: '充值订单',
    requiredPermissions: portalPermission,
    groupKey: 'order-management',
    availability: 'available',
    navigationVisible: true
  },
  {
    key: 'withdrawals',
    path: '/withdrawals',
    pageId: 'OPS-16',
    title: '提现订单',
    requiredPermissions: portalPermission,
    groupKey: 'order-management',
    availability: 'available',
    navigationVisible: true
  },
  {
    key: 'food-orders',
    path: '/food-orders',
    pageId: 'OPS-17',
    title: '外卖订单',
    requiredPermissions: portalPermission,
    groupKey: 'order-management',
    availability: 'available',
    navigationVisible: true
  },
  {
    key: 'collection-records',
    path: '/collection-records',
    pageId: 'OPS-18',
    title: '收款记录',
    requiredPermissions: portalPermission,
    groupKey: 'order-management',
    availability: 'available',
    navigationVisible: true
  },
  {
    key: 'notifications',
    path: '/notifications',
    pageId: 'OPS-08',
    title: '商户通知',
    requiredPermissions: portalPermission,
    groupKey: 'order-management',
    availability: 'available',
    navigationVisible: true
  },
  {
    key: 'login-audits',
    path: '/system/audits/login',
    pageId: 'OPS-12',
    title: '登录审计',
    requiredPermissions: auditPermissions,
    availability: 'available',
    navigationVisible: false
  }
];

export function findOpsRouteByKey(key: OpsRouteKey): OpsRouteMeta {
  const route = opsRoutes.find((item) => item.key === key);
  if (!route) throw new Error(`Unknown OPS route key: ${key}`);
  return route;
}

export function findOpsMenuGroupByKey(key: OpsMenuGroupKey): OpsMenuGroup {
  const group = opsMenuGroups.find((item) => item.key === key);
  if (!group) throw new Error(`Unknown OPS menu group key: ${key}`);
  return group;
}

export function findOpsRouteByPath(pathname: string): OpsRouteMeta | undefined {
  const normalizedPath = pathname.length > 1 ? pathname.replace(/\/+$/, '') : pathname;
  return opsRoutes.find((item) => item.path === normalizedPath);
}

export function getOpsBreadcrumbTitles(route: OpsRouteMeta): string[] {
  if (route.key === 'dashboard') return ['主页'];
  return [
    '主页',
    ...(route.groupKey ? [findOpsMenuGroupByKey(route.groupKey).title] : []),
    route.title
  ];
}

export function hasOpsRouteAccess(
  admin: Pick<AuthenticatedAdmin, 'roles' | 'permissions'>,
  route: OpsRouteMeta
): boolean {
  return (
    admin.roles.includes('platform_admin') &&
    route.requiredPermissions.every((permission) => admin.permissions.includes(permission))
  );
}

export function visibleOpsRoutes(
  admin: Pick<AuthenticatedAdmin, 'roles' | 'permissions'>
): OpsRouteMeta[] {
  return opsRoutes.filter(
    (route) => route.navigationVisible && hasOpsRouteAccess(admin, route)
  );
}
