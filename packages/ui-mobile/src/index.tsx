import type { PropsWithChildren } from 'react';

export function MobileSection({ children }: PropsWithChildren) {
  return <section aria-label="MiniPay mobile section">{children}</section>;
}
