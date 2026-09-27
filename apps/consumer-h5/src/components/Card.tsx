import type { PropsWithChildren, ReactNode } from 'react';
import common from './common.module.less';

export interface CardProps {
  title?: ReactNode;
  action?: ReactNode;
  tight?: boolean;
}

/** 统一的卡片容器；标题使用语义化 h2，便于读屏定位。 */
export function Card({ title, action, tight, children }: PropsWithChildren<CardProps>) {
  return (
    <section className={tight ? `${common.card} ${common.cardTight}` : common.card}>
      {title ? (
        <header className={common.row} style={{ padding: 0, marginBottom: 8 }}>
          <h2 className={common.cardTitle} style={{ marginBottom: 0 }}>
            {title}
          </h2>
          {action}
        </header>
      ) : null}
      {children}
    </section>
  );
}
