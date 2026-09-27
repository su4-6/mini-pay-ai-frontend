import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

/**
 * Umi 会把 `src/app.tsx` 的**每个导出**当作运行时插件注册，导出名必须落在
 * 生成的 `src/.umi/core/plugin.ts` 里 `getValidKeys()` 的白名单中；
 * 否则 `pluginManager.register()` 的断言会直接抛出
 * `register failed, invalid key <name>`，整个应用启动中断、页面白屏。
 *
 * 2026-09-27 实际踩到：`export const queryClient = new QueryClient(...)` 让 H5 白屏，
 * 而当时所有验收只查了「index.html 可访问 + 资源 200」，没有任何浏览器渲染断言，
 * 所以线上坏了很久都没被发现 —— 这个用例就是那道闸门。
 */
const VALID_KEYS = [
  'patchRoutes',
  'patchClientRoutes',
  'modifyContextOpts',
  'modifyClientRenderOpts',
  'rootContainer',
  'innerProvider',
  'i18nProvider',
  'accessProvider',
  'dataflowProvider',
  'outerProvider',
  'render',
  'onRouteChange',
  'modifyServerLoaderRequest',
  'qiankun',
  'request'
];

describe('app.tsx 导出白名单', () => {
  it('只导出 Umi 认可的运行时 key（不得多导，例如 queryClient）', () => {
    const source = readFileSync(new URL('./app.tsx', import.meta.url), 'utf8');
    const names = [...source.matchAll(/^\s*export\s+(?:const|function|async function|let|var)\s+([A-Za-z0-9_]+)/gm)].map(
      (match) => match[1]
    );

    expect(names.length).toBeGreaterThan(0);
    expect(names.filter((name) => !VALID_KEYS.includes(name))).toEqual([]);
  });

  /**
   * 2026-09-27 的第二个根因：生成的 `.umi/plugin-request/request.ts` 底层是 **axios**，
   * 而 umi-request 那种「返回 `[url, options]` 元组」的拦截器写法会被它解构成
   * `{ url: newUrl, options }` 再 `{ ...options, url }` —— 配置里丢掉 `method`，
   * axios 随即在 `config.method.toUpperCase()` 抛异常，被 `services/http.ts` 归一化成
   * 「网络不可用」，于是所有 GET（含会话查询）全挂、H5 打不开。
   */
  it('request 配置不得声明 requestInterceptors（axios 拦截器签名不同）', () => {
    const source = readFileSync(new URL('./app.tsx', import.meta.url), 'utf8');
    // 只看真正的配置项（注释里解释这个坑时会出现该词，不能误判）
    expect(/^\s*requestInterceptors\s*:/m.test(source)).toBe(false);
  });
});
