import { Link, useLocation, useNavigate } from '@umijs/max';
import { NavBar } from 'antd-mobile';
import { House, MessageCircle, ReceiptText, UserRound, type LucideIcon } from 'lucide-react';
import type { PropsWithChildren, ReactNode } from 'react';
import { ROUTES } from '../constants/routes';
import styles from './AppShell.module.less';

interface TabItem {
  path: string;
  label: string;
  Icon: LucideIcon;
  exact?: boolean;
}

const TAB_ITEMS: TabItem[] = [
  { path: ROUTES.home, label: '首页', Icon: House, exact: true },
  { path: ROUTES.chat, label: '米灵', Icon: MessageCircle, exact: true },
  { path: ROUTES.bills, label: '账单', Icon: ReceiptText },
  { path: ROUTES.me, label: '我的', Icon: UserRound }
];

export interface AppShellProps {
  title: string;
  subtitle?: string;
  /** 提供时展示返回按钮并跳转到该路径；不提供则无返回按钮。 */
  backTo?: string;
  right?: ReactNode;
  showTabBar?: boolean;
  /** 首页这类沉浸式页面可隐藏白色顶部栏。 */
  headerless?: boolean;
  /** 内容区去掉默认内边距（聊天页等需要撑满的场景）。 */
  flush?: boolean;
}

export function AppShell({
  title,
  subtitle,
  backTo,
  right,
  showTabBar,
  headerless,
  flush,
  children
}: PropsWithChildren<AppShellProps>) {
  const navigate = useNavigate();
  const location = useLocation();

  const mainClassName = [
    styles.main,
    flush ? styles.mainFlush : '',
    showTabBar ? styles.mainWithTabBar : ''
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={styles.shell}>
      {!headerless ? (
        <header className={styles.header}>
          <NavBar
            className={styles.navbar}
            back={backTo ? '' : null}
            onBack={() => {
              if (backTo) navigate(backTo);
              else navigate(-1);
            }}
            right={right}
          >
            <span className={styles.title}>{title}</span>
          </NavBar>
          {subtitle ? <p className={styles.subtitle}>{subtitle}</p> : null}
        </header>
      ) : null}
      <main className={mainClassName}>{children}</main>
      {showTabBar ? (
        <nav className={styles.tabbar} aria-label="主导航">
          {TAB_ITEMS.map(({ path, label, Icon, exact }) => {
            const active = exact ? location.pathname === path : location.pathname.startsWith(path);
            return (
              <Link
                key={path}
                to={path}
                className={active ? `${styles.tabItem} ${styles.tabItemActive}` : styles.tabItem}
                aria-current={active ? 'page' : undefined}
              >
                <Icon className={styles.tabIcon} aria-hidden strokeWidth={active ? 2.4 : 1.9} />
                <span>{label}</span>
              </Link>
            );
          })}
        </nav>
      ) : null}
    </div>
  );
}
