const RECOVERY_KEY = 'minipay:chunk-recovery';
const RECOVERY_WINDOW_MS = 60_000;

/** Umi/webpack 页面分包或样式分包加载失败的常见错误特征。 */
export function isChunkLoadFailure(reason: unknown): boolean {
  const message =
    reason instanceof Error
      ? `${reason.name} ${reason.message}`
      : typeof reason === 'string'
        ? reason
        : reason && typeof reason === 'object' && 'message' in reason
          ? String((reason as { message?: unknown }).message ?? '')
          : '';

  return /ChunkLoadError|Loading (?:CSS )?chunk|Failed to fetch dynamically imported module|Importing a module script failed/i.test(
    message
  );
}

/**
 * 分包偶发失败时只自动刷新一次。刷新时间写入 sessionStorage，避免网络长期异常造成刷新循环。
 */
export function recoverChunkLoadFailure(reason: unknown, currentTime = Date.now()): boolean {
  if (typeof window === 'undefined' || !isChunkLoadFailure(reason)) return false;

  const previous = Number(window.sessionStorage.getItem(RECOVERY_KEY) ?? 0);
  if (Number.isFinite(previous) && currentTime - previous < RECOVERY_WINDOW_MS) return false;

  window.sessionStorage.setItem(RECOVERY_KEY, String(currentTime));
  window.location.reload();
  return true;
}

export function installChunkRecovery(): () => void {
  if (typeof window === 'undefined') return () => undefined;

  const onError = (event: ErrorEvent) => {
    recoverChunkLoadFailure(event.error ?? event.message);
  };
  const onUnhandledRejection = (event: PromiseRejectionEvent) => {
    recoverChunkLoadFailure(event.reason);
  };

  window.addEventListener('error', onError);
  window.addEventListener('unhandledrejection', onUnhandledRejection);
  return () => {
    window.removeEventListener('error', onError);
    window.removeEventListener('unhandledrejection', onUnhandledRejection);
  };
}
