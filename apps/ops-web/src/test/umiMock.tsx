import type { AnchorHTMLAttributes, ReactNode } from 'react';

export function Link({
  to,
  children,
  ...props
}: AnchorHTMLAttributes<HTMLAnchorElement> & { to: string; children?: ReactNode }) {
  return <a href={to} {...props}>{children}</a>;
}

export function useLocation() {
  return { pathname: window.location.pathname, search: window.location.search };
}

export function useNavigate() {
  return (path: string) => {
    window.history.pushState({}, '', path);
  };
}
