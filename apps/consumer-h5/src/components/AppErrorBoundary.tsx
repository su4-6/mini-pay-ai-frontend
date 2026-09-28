import { Component, type PropsWithChildren } from 'react';
import { isChunkLoadFailure, recoverChunkLoadFailure } from '../utils/chunk-recovery';
import styles from './AppErrorBoundary.module.less';

interface State {
  error?: Error;
}

/** 页面渲染兜底：不再把运行时错误留成纯白屏。 */
export class AppErrorBoundary extends Component<PropsWithChildren, State> {
  state: State = {};

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error): void {
    recoverChunkLoadFailure(error);
  }

  render() {
    if (!this.state.error) return this.props.children;

    const chunkFailure = isChunkLoadFailure(this.state.error);
    return (
      <main className={styles.page} role="alert">
        <section className={styles.panel}>
          <div className={styles.mark} aria-hidden>M</div>
          <h1>{chunkFailure ? '页面资源加载中断' : '页面暂时没有正常显示'}</h1>
          <p>
            {chunkFailure
              ? '网络波动导致页面资源没有完整到达，重新加载即可继续。'
              : '你的操作尚未提交。可以重新加载当前页面，或先返回首页。'}
          </p>
          <div className={styles.actions}>
            <button className={styles.secondary} type="button" onClick={() => window.location.assign('/')}>
              返回首页
            </button>
            <button className={styles.primary} type="button" onClick={() => window.location.reload()}>
              重新加载
            </button>
          </div>
        </section>
      </main>
    );
  }
}
