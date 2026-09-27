import { useEffect, useState } from 'react';

/** 每秒（或指定间隔）推进一次的时钟，用于有效期倒计时；不参与任何服务端数据派生。 */
export function useNow(intervalMs = 1_000): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = globalThis.setInterval(() => setNow(Date.now()), intervalMs);
    return () => {
      globalThis.clearInterval(timer);
    };
  }, [intervalMs]);
  return now;
}
