import type { ReactNode } from 'react';
import common from './common.module.less';

export interface InlineNoticeProps {
  children: ReactNode;
  tone?: 'info' | 'warning' | 'danger';
  /** 诊断用 requestId，仅用于客服定位，不暴露内部细节。 */
  requestId?: string;
}

function toneClass(tone: InlineNoticeProps['tone']): string {
  if (tone === 'danger') return common.noticeDanger;
  if (tone === 'warning') return common.noticeWarning;
  return '';
}

/** 纯文案提示（表单校验、流程说明等），与 ProblemNotice 区分：不解析异常对象。 */
export function InlineNotice({ children, tone = 'info', requestId }: InlineNoticeProps) {
  return (
    <div className={`${common.notice} ${toneClass(tone)}`.trim()} role="status">
      <div className={common.noticeBody}>
        <div>{children}</div>
        {requestId ? <div className={common.requestId}>requestId: {requestId}</div> : null}
      </div>
    </div>
  );
}
