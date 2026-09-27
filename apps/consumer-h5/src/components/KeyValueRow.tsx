import type { ReactNode } from 'react';
import common from './common.module.less';

export interface KeyValueRowProps {
  label: string;
  value: ReactNode;
  mono?: boolean;
}

/** 键值行：`dt`/`dd` 语义，标签与值成对出现。 */
export function KeyValueRow({ label, value, mono }: KeyValueRowProps) {
  return (
    <div className={common.row}>
      <dt className={common.rowLabel}>{label}</dt>
      <dd className={common.rowValue} style={{ margin: 0 }}>
        <span className={mono ? common.mono : undefined}>{value}</span>
      </dd>
    </div>
  );
}
