/**
 * Umi 异步路由的全局加载界面。
 *
 * 没有该文件时 Umi 默认只渲染空 div；移动网络第一次下载页面分包期间看起来就是白屏。
 * 这里不依赖组件库，确保最小基础包到达后立刻能显示反馈。
 */
export default function RouteLoading() {
  return (
    <main
      role="status"
      aria-live="polite"
      style={{
        display: 'grid',
        minHeight: '100dvh',
        placeItems: 'center',
        background: '#f4f7fb',
        color: '#667085'
      }}
    >
      <section style={{ display: 'grid', justifyItems: 'center', gap: 12 }}>
        <span
          aria-hidden
          style={{
            width: 34,
            height: 34,
            border: '3px solid #d8e5ff',
            borderTopColor: '#1677ff',
            borderRadius: '50%',
            animation: 'mp-route-spin .8s linear infinite'
          }}
        />
        <span>页面加载中…</span>
        <style>{'@keyframes mp-route-spin{to{transform:rotate(360deg)}}'}</style>
      </section>
    </main>
  );
}
