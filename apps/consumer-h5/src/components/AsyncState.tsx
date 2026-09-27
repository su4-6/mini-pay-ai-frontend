import { Button, SpinLoading } from 'antd-mobile';
import { ExclamationCircleOutline } from 'antd-mobile-icons';
import type { ReactNode } from 'react';
import { describeProblem } from '../services/problem';
import common from './common.module.less';

export interface AsyncStateProps {
  loading?: boolean;
  error?: unknown;
  /** 请求成功但没有数据。 */
  empty?: boolean;
  emptyText?: string;
  loadingText?: string;
  onRetry?: () => void;
  retryText?: string;
  children?: ReactNode;
}

/**
 * 统一的加载 / 空数据 / 失败（含重试）状态包装。
 * 覆盖 PROJECT_STANDARDS §6 要求的加载、空数据、无权限、网络失败与重试。
 */
export function AsyncState({
  loading,
  error,
  empty,
  emptyText = '暂无数据',
  loadingText = '加载中…',
  onRetry,
  retryText = '重试',
  children
}: AsyncStateProps) {
  if (loading) {
    return (
      <div className={common.centerBox} role="status" aria-live="polite">
        <SpinLoading color="primary" />
        <p className={common.muted}>{loadingText}</p>
      </div>
    );
  }
  if (error !== undefined && error !== null) {
    const view = describeProblem(error);
    return (
      <div className={common.centerBox} role="alert">
        <ExclamationCircleOutline fontSize={28} className={common.danger} aria-hidden />
        <p>{view.message}</p>
        {view.requestId && view.requestId !== 'unknown' ? (
          <p className={common.requestId}>requestId: {view.requestId}</p>
        ) : null}
        {onRetry ? (
          <Button color="primary" size="small" onClick={onRetry}>
            {retryText}
          </Button>
        ) : null}
      </div>
    );
  }
  if (empty) {
    return (
      <div className={common.centerBox}>
        <p className={common.muted}>{emptyText}</p>
      </div>
    );
  }
  return <>{children}</>;
}
