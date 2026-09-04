import { useEffect, useMemo, useState } from 'react';
import { Menu, type MenuProps } from 'antd';
import {
  findOpsRouteByKey,
  opsMenuGroups,
  type OpsRouteKey,
  type OpsRouteMeta
} from '../config/navigation';
import styles from './OpsNavigation.module.less';

interface OpsNavigationProps {
  routes: OpsRouteMeta[];
  activeKey: OpsRouteKey;
  collapsed?: boolean;
  onNavigate: (route: OpsRouteMeta) => void;
}

const railMark = (label: string) => <span className={styles.railMark} aria-hidden="true">{label.slice(0, 1)}</span>;

export function OpsNavigation({
  routes,
  activeKey,
  collapsed = false,
  onNavigate
}: OpsNavigationProps) {
  const activeRoute = findOpsRouteByKey(activeKey);
  const [openKeys, setOpenKeys] = useState<string[]>(
    collapsed ? [] : opsMenuGroups.map((group) => group.key)
  );

  useEffect(() => {
    if (collapsed) {
      setOpenKeys([]);
    } else {
      setOpenKeys(opsMenuGroups.map((group) => group.key));
    }
  }, [activeRoute.groupKey, collapsed]);

  const menuItems = useMemo<NonNullable<MenuProps['items']>>(() => {
    const items: NonNullable<MenuProps['items']> = [];
    const dashboard = routes.find((route) => route.key === 'dashboard');
    if (dashboard) {
      items.push({
        key: dashboard.key,
        icon: collapsed ? railMark(dashboard.title) : undefined,
        label: dashboard.title,
        title: dashboard.title
      });
    }

    opsMenuGroups.forEach((group) => {
      const children = routes
        .filter((route) => route.groupKey === group.key)
        .map((route) => ({
          key: route.key,
          disabled: route.availability === 'planned',
          label: route.availability === 'planned' ? (
            <span className={styles.plannedLabel}>
              <span>{route.title}</span>
              <span className={styles.plannedBadge}>待开发</span>
            </span>
          ) : route.title,
          title: route.title
        }));
      if (children.length > 0) {
        items.push({
          key: group.key,
          icon: collapsed ? railMark(group.title) : undefined,
          label: group.title,
          title: group.title,
          children
        });
      }
    });
    return items;
  }, [collapsed, routes]);

  const handleOpenChange: MenuProps['onOpenChange'] = (keys) => {
    setOpenKeys(keys);
  };

  const handleClick: MenuProps['onClick'] = ({ key }) => {
    const route = routes.find((item) => item.key === key);
    if (route) {
      onNavigate(route);
    }
  };

  return (
    <nav className={styles.navigation} aria-label="运营平台主导航">
      <Menu
        theme="light"
        mode="inline"
        inlineCollapsed={collapsed}
        triggerSubMenuAction={collapsed ? 'click' : 'hover'}
        items={menuItems}
        selectedKeys={[activeKey]}
        openKeys={openKeys}
        onOpenChange={handleOpenChange}
        onClick={handleClick}
      />
    </nav>
  );
}
