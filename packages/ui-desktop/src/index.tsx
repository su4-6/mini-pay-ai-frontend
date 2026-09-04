import React, { useEffect, useState, type CSSProperties, type PropsWithChildren } from 'react';
import './desktop.css';

export const DESKTOP_SIDEBAR_WIDTH = 232;
export const DESKTOP_SIDEBAR_COLLAPSED_WIDTH = 72;

/** Shared Ant Design Pro-inspired theme for all three desktop portals. */
export const minipayDesktopTheme = {
  cssVar: { prefix: 'minipay' },
  token: {
    colorPrimary: '#155eef',
    colorInfo: '#155eef',
    colorSuccess: '#16a34a',
    colorWarning: '#d97706',
    colorError: '#dc2626',
    colorText: '#182230',
    colorTextSecondary: '#667085',
    colorBgLayout: '#f3f6fb',
    colorBorderSecondary: '#e4eaf2',
    borderRadius: 10,
    borderRadiusLG: 16,
    controlHeight: 38,
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif",
    boxShadowSecondary: '0 12px 32px rgba(30, 64, 175, 0.10)'
  },
  components: {
    Button: { borderRadius: 9, primaryShadow: '0 7px 18px rgba(21, 94, 239, 0.20)' },
    Card: { headerBg: 'transparent', paddingLG: 22 },
    Layout: { bodyBg: '#f3f6fb', headerBg: 'rgba(255,255,255,.88)', siderBg: '#fff' },
    Menu: { itemBorderRadius: 10, itemHeight: 44, itemMarginInline: 6, itemSelectedBg: '#eaf2ff', itemSelectedColor: '#155eef' },
    Table: { headerBg: '#f7f9fc', headerColor: '#475467', rowHoverBg: '#f7faff', cellPaddingBlock: 15 },
    Tabs: { itemSelectedColor: '#155eef', inkBarColor: '#155eef' }
  }
};

export function DesktopSection({ children }: PropsWithChildren) {
  return <section aria-label="MiniPay desktop section">{children}</section>;
}

export type PortalKey = 'merchant' | 'ops' | 'admin';
export interface PortalSwitcherProps { current: PortalKey; urls: Record<PortalKey, string>; }
const portalLabels: Record<PortalKey, string> = {
  merchant: '商户端登录', ops: '运营端登录', admin: '管理端登录'
};

function portalSwitchUrl(portalUrl: string, target: PortalKey) {
  const origin = typeof window === 'undefined' ? 'http://localhost' : window.location.origin;
  const base = new URL(portalUrl, origin);
  if (!base.pathname.endsWith('/')) base.pathname += '/';
  return new URL(`switch-login?target=${target}`, base).toString();
}

/** Keeps portal discovery consistent and always starts a fresh target portal session. */
export function PortalSwitcher({ current, urls }: PortalSwitcherProps) {
  const rootStyle: CSSProperties = {
    display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 8,
    marginTop: 18, padding: 6, borderRadius: 12, background: 'rgba(15, 23, 42, 0.06)'
  };
  const linkStyle: CSSProperties = {
    display: 'flex', minHeight: 36, alignItems: 'center', justifyContent: 'center',
    borderRadius: 8, color: '#475569', fontSize: 13, fontWeight: 600,
    textDecoration: 'none', transition: 'background-color .2s ease,color .2s ease'
  };
  return <nav aria-label="切换登录入口" style={rootStyle}>
    {(Object.keys(portalLabels) as PortalKey[]).map(key => key === current
      ? <span key={key} aria-current="page" style={{...linkStyle, color:'#fff', background:'#1677ff'}}>{portalLabels[key]}</span>
      : <a key={key} href={portalSwitchUrl(urls[key], key)} style={linkStyle}>{portalLabels[key]}</a>)}
  </nav>;
}

export function useSidebarPreference(storageKey: string) {
  const [collapsed, setCollapsed] = useState(() => typeof window !== 'undefined'
    && window.localStorage.getItem(storageKey) === 'true');
  useEffect(() => {
    window.localStorage.setItem(storageKey, String(collapsed));
  }, [collapsed, storageKey]);
  return { collapsed, setCollapsed, toggleCollapsed: () => setCollapsed((value: boolean) => !value) };
}
