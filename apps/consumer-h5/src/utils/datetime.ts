import dayjs from 'dayjs';

/** 展示用时间：同年只显示「MM-DD HH:mm」，跨年补上年份。 */
export function formatDateTime(value?: string): string {
  if (!value) return '--';
  const parsed = dayjs(value);
  if (!parsed.isValid()) return '--';
  return parsed.year() === dayjs().year() ? parsed.format('MM-DD HH:mm') : parsed.format('YYYY-MM-DD HH:mm');
}

export function formatDate(value?: string): string {
  if (!value) return '--';
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD') : '--';
}

/** 剩余有效期的可读文案，例如 `剩余 04:59`。 */
export function remainingLabel(expiresAt: string | undefined, nowMs: number): string {
  if (!expiresAt) return '';
  const expiresMs = dayjs(expiresAt).valueOf();
  if (!Number.isFinite(expiresMs)) return '';
  const remainingMs = expiresMs - nowMs;
  if (remainingMs <= 0) return '已过期';
  const totalSeconds = Math.floor(remainingMs / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `剩余 ${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

export function isExpired(expiresAt: string | undefined, nowMs: number): boolean {
  if (!expiresAt) return false;
  const expiresMs = dayjs(expiresAt).valueOf();
  return Number.isFinite(expiresMs) && expiresMs <= nowMs;
}

export function formatRelative(value?: string, nowMs: number = Date.now()): string {
  if (!value) return '';
  const parsed = dayjs(value);
  if (!parsed.isValid()) return '';
  const diffMinutes = Math.floor((nowMs - parsed.valueOf()) / 60_000);
  if (diffMinutes < 1) return '刚刚';
  if (diffMinutes < 60) return `${diffMinutes} 分钟前`;
  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours} 小时前`;
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 7) return `${diffDays} 天前`;
  return formatDateTime(value);
}
