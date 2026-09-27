/**
 * 登录跳转参数处理。只接受同源站内路径，避免开放重定向。
 */
export function sanitizeRedirect(raw: string | null | undefined, fallback: string): string {
  if (!raw) return fallback;
  let decoded = raw;
  try {
    decoded = decodeURIComponent(raw);
  } catch {
    return fallback;
  }
  if (!decoded.startsWith('/') || decoded.startsWith('//')) return fallback;
  if (/^\/\\/.test(decoded)) return fallback;
  return decoded;
}

export function withRedirect(path: string, currentUrl: string): string {
  return `${path}?redirect=${encodeURIComponent(currentUrl)}`;
}

export function readSearchParam(search: string, key: string): string | null {
  const params = new URLSearchParams(search.startsWith('?') ? search.slice(1) : search);
  const value = params.get(key);
  return value && value.trim() ? value.trim() : null;
}
