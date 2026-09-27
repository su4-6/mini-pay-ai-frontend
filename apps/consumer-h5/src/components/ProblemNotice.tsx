import { describeProblem, type ProblemView } from '../services/problem';
import common from './common.module.less';

export interface ProblemNoticeProps {
  error: unknown;
  /** 覆盖默认文案。 */
  message?: string;
  tone?: 'info' | 'warning' | 'danger';
}

function toneClass(tone: ProblemNoticeProps['tone']): string {
  if (tone === 'danger') return common.noticeDanger;
  if (tone === 'warning') return common.noticeWarning;
  return '';
}

/** 面向用户的错误提示：可读文案 + 保留 requestId（不暴露堆栈或内部地址）。 */
export function ProblemNotice({ error, message, tone }: ProblemNoticeProps) {
  const view: ProblemView = describeProblem(error);
  const className = [common.notice, toneClass(tone ?? (view.problemClass === 'RETRYABLE' ? 'warning' : 'danger'))]
    .filter(Boolean)
    .join(' ');
  return (
    <div className={className} role="alert">
      <div className={common.noticeBody}>
        <div>{message ?? view.message}</div>
        {view.requestId && view.requestId !== 'unknown' ? (
          <div className={common.requestId}>requestId: {view.requestId}</div>
        ) : null}
      </div>
    </div>
  );
}
