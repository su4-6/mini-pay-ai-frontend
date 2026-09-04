import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode
} from 'react';
import type { AuthenticatedAdmin } from '@minipay/api-contracts';
import { App, Breadcrumb, Button, Drawer, Dropdown, Tooltip, type MenuProps } from 'antd';
import { BellOutlined, ReloadOutlined } from '@ant-design/icons';
import { Link, useLocation, useNavigate } from '@umijs/max';
import { useQueryClient } from '@tanstack/react-query';
import {
  findOpsMenuGroupByKey,
  findOpsRouteByKey,
  type OpsRouteKey,
  type OpsRouteMeta,
  visibleOpsRoutes
} from '../config/navigation';
import { useSecureLogout } from '../hooks/useSecureLogout';
import { useSidebarPreference } from '../hooks/useSidebarPreference';
import { OpsNavigation } from './OpsNavigation';
import styles from './OpsShell.module.less';

const publicPath = typeof MINIPAY_PUBLIC_PATH === 'string' ? MINIPAY_PUBLIC_PATH : '/ops/';

interface OpsShellProps {
  admin: AuthenticatedAdmin;
  activeKey: OpsRouteKey;
  children: ReactNode;
}

function MenuToggleIcon({ collapsed = false }: { collapsed?: boolean }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      {collapsed
        ? <path d="M9 5 7.6 6.4l5.6 5.6-5.6 5.6L9 19l7-7-7-7Z" />
        : <path d="M15 5 8 12l7 7 1.4-1.4-5.6-5.6 5.6-5.6L15 5Z" />}
    </svg>
  );
}

function MobileMenuIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3 5h18v2H3V5Zm0 6h18v2H3v-2Zm0 6h18v2H3v-2Z" />
    </svg>
  );
}

function Brand({
  collapsed = false,
  onNavigate
}: {
  collapsed?: boolean;
  onNavigate: () => void;
}) {
  return (
    <Tooltip title={collapsed ? '返回主页' : undefined} placement="right">
      <button
        className={styles.logo}
        type="button"
        aria-label="返回主页"
        onClick={onNavigate}
      >
        <img className={styles.logoMark} src={`${publicPath}minipay-logo.jpg`} alt="" />
        {!collapsed ? <span className={styles.logoText}>minipay</span> : null}
      </button>
    </Tooltip>
  );
}

export function OpsShell({ admin, activeKey, children }: OpsShellProps) {
  const { message } = App.useApp();
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const mobileMenuButtonRef = useRef<HTMLButtonElement>(null);
  const { collapsed, toggleCollapsed } = useSidebarPreference();
  const activeRoute = findOpsRouteByKey(activeKey);
  const routes = useMemo(
    () => visibleOpsRoutes({
      roles: admin.roles,
      permissions: admin.permissions ?? []
    }),
    [admin.permissions, admin.roles]
  );
  const { loggingOut, logout } = useSecureLogout({
    onError: () => {
      void message.error('退出失败，请检查网络后重试。');
    }
  });

  const userMenu: MenuProps['items'] = [
    {
      key: 'identity',
      disabled: true,
      label: (
        <div className={styles.userSummary}>
          <strong>{admin.displayName}</strong>
          <span>平台管理员 · platform_admin</span>
        </div>
      )
    },
    { type: 'divider' },
    {
      key: 'logout',
      danger: true,
      disabled: loggingOut,
      label: loggingOut ? '正在退出…' : '退出登录',
      onClick: () => void logout()
    }
  ];

  const navigateToRoute = (route: OpsRouteMeta) => {
    setMobileMenuOpen(false);
    navigate(route.path);
  };

  const refreshCurrentData = async () => {
    setRefreshing(true);
    try {
      await queryClient.invalidateQueries({
        predicate: (query) => query.queryKey[0] !== 'session',
        refetchType: 'active'
      });
      void message.success('当前页面数据已刷新');
    } catch {
      void message.error('刷新失败，请稍后重试。');
    } finally {
      setRefreshing(false);
    }
  };

  const breadcrumbItems = [
    ...(activeRoute.key === 'dashboard'
      ? []
      : [{ title: <Link to="/">主页</Link> }]),
    ...(activeRoute.groupKey
      ? [{ title: findOpsMenuGroupByKey(activeRoute.groupKey).title }]
      : activeRoute.key === 'login-audits'
        ? [{ title: '安全审计' }]
        : []),
    { title: activeRoute.title }
  ];

  useEffect(() => {
    setMobileMenuOpen(false);
    window.scrollTo({ top: 0, behavior: 'auto' });
  }, [location.pathname]);

  const shellStyle = {
    '--ops-sidebar-width': collapsed ? '72px' : '232px'
  } as CSSProperties;

  const sidebarLabel = collapsed ? '展开侧栏' : '收起侧栏';

  return (
    <div className={`${styles.shell} minipay-desktop-shell`} style={shellStyle}>
      <a className={styles.skipLink} href="#ops-main-content">跳到主要内容</a>

      <aside className={`${styles.sidebar} minipay-desktop-sidebar ${collapsed ? styles.sidebarCollapsed : ''}`}>
        <Brand collapsed={collapsed} onNavigate={() => navigate('/')} />
        <OpsNavigation
          routes={routes}
          activeKey={activeKey}
          collapsed={collapsed}
          onNavigate={navigateToRoute}
        />
        {!collapsed ? <div className={styles.sidebarFooter}>MiniPay 运营平台</div> : null}
      </aside>

      <Drawer
        className={styles.mobileDrawer}
        placement="left"
        size={248}
        open={mobileMenuOpen}
        onClose={() => setMobileMenuOpen(false)}
        afterOpenChange={(open) => {
          if (!open) {
            mobileMenuButtonRef.current?.focus();
          }
        }}
        title="运营平台导航"
      >
        <Brand onNavigate={() => navigate('/')} />
        <OpsNavigation
          routes={routes}
          activeKey={activeKey}
          onNavigate={navigateToRoute}
        />
      </Drawer>

      <div className={styles.workspace}>
        <header className={`${styles.header} minipay-desktop-header`}>
          <div className={styles.headerStart}>
            <Tooltip title={mobileMenuOpen ? '关闭主导航' : '打开主导航'}>
              <Button
                ref={mobileMenuButtonRef}
                type="text"
                className={styles.mobileMenuToggle}
                aria-label={mobileMenuOpen ? '关闭主导航' : '打开主导航'}
                aria-expanded={mobileMenuOpen}
                onClick={() => setMobileMenuOpen((open) => !open)}
              >
                <MobileMenuIcon />
              </Button>
            </Tooltip>

            <Tooltip title={sidebarLabel}>
              <Button
                type="text"
                className={styles.desktopCollapse}
                aria-label={sidebarLabel}
                aria-expanded={!collapsed}
                onClick={toggleCollapsed}
              >
                <MenuToggleIcon collapsed={collapsed} />
              </Button>
            </Tooltip>

            <Tooltip title="刷新当前页面数据">
              <Button
                type="text"
                className={styles.headerAction}
                aria-label="刷新当前页面数据"
                loading={refreshing}
                icon={!refreshing ? <ReloadOutlined /> : undefined}
                onClick={() => void refreshCurrentData()}
              />
            </Tooltip>
          </div>

          <div className={styles.headerEnd}>
            <Tooltip title="通知功能待开发">
              <span>
                <Button
                  type="text"
                  className={styles.headerAction}
                  aria-label="通知功能待开发"
                  icon={<BellOutlined />}
                  disabled
                />
              </span>
            </Tooltip>
            <Dropdown menu={{ items: userMenu }} placement="bottomRight" trigger={['click']}>
              <Button
                type="text"
                className={styles.userButton}
                aria-label={`管理员菜单：${admin.displayName}`}
                aria-haspopup="menu"
              >
                <span className={styles.avatar}>{admin.displayName.slice(0, 1)}</span>
                <span className={styles.displayName}>{admin.displayName}</span>
              </Button>
            </Dropdown>
          </div>
        </header>
        <div className={styles.breadcrumbBar}>
          <Breadcrumb className={styles.breadcrumb} items={breadcrumbItems} />
        </div>
        <main id="ops-main-content" className={`${styles.content} minipay-desktop-content`} tabIndex={-1}>
          {children}
        </main>
      </div>
    </div>
  );
}
