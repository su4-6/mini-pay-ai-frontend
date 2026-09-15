# 前端性能与打包记录

本文件保存三端控制台的构建配置取舍与实测数据，供后续优化时对比。结论先行：**首屏传输 414 KB → 325 KB**，
关键手段是压缩器、按需加载地图 SDK、图片瘦身与按 chunk 拆分。

## 1. 构建配置

| 配置 | 位置 | 作用 |
| --- | --- | --- |
| `codeSplitting: { jsStrategy: 'granularChunks' }` | `apps/*/.umirc.ts` | 按 npm 包拆分 vendor，避免出现单个巨型 chunk |
| `jsMinifier: 'terser'` | 同上 | 比 esbuild 压缩率更高（见下表） |
| `mfsu: false` | 同上 | 生产构建不做依赖预打包，产物稳定可复现 |
| 高德 JS SDK 异步注入 | 地图组件 | 只有真正出现地图的页面才加载 `webapi.amap.com` |

> terser 不识别 esbuild 的 `charset` 选项，构建时会直接 `DefaultsError: charset is not a supported option`；
> terser 默认输出就是 UTF-8，删掉该选项即可。

## 2. 实测（ops 登录页，无头浏览器冷加载 / 热加载）

| 指标 | esbuild（0.1.0-k3s.8） | terser（0.1.0-k3s.9） | 变化 |
| --- | --- | --- | --- |
| script 传输（gzip） | 358 KB | **310 KB** | −13% |
| 图片传输（logo + 验证码） | 48 KB | **11 KB** | −77% |
| **首屏总传输** | **414 KB** | **325 KB** | **−21%** |
| 热加载传输 | ~5 KB | 5 KB | — |
| 控制台报错 | 0 | 0 | — |

- 单文件：`umi.js` 223 → **189 KB**，`237.*.async.js` 324 → **299 KB**（gzip，按 umi 构建报告）。
- 本地 dist 原始体积：admin-web 1599 → 1461 KB、ops-web 3468 → 3232 KB、merchant-web 3159 → 2947 KB。
- logo 从 1024×1024 缩到 256×256（q82）：**45,974 B → 8,165 B**。

冷/热加载的采集方式：无头浏览器打开页面记录 `Performance` 与 `Network` 事件，逐项汇总传输字节数。

## 3. 缓存策略（镜像内 nginx）

| 资源 | 策略 | 原因 |
| --- | --- | --- |
| HTML 外壳 | `Cache-Control: no-cache` | 保证前端修复能立刻生效 |
| 带 hash 的 JS/CSS/字体 | `public, max-age=604800, immutable` | 文件名即版本，可长期边缘缓存 |
| 未哈希的图片（logo 等） | 跟随默认策略 | **换文件后必须清理 CDN 缓存**，否则边缘继续供旧图 |

> 踩过的坑：`minipay-logo.jpg` 没有 hash 又带 `immutable`，替换后 Cloudflare 边缘 33 小时仍在供旧文件。
> 未哈希资源更新后必须 purge（或改成带 hash 的 URL）。

## 4. 还可继续优化的方向

- 运营总览的图表依赖（`@ant-design/plots` + `antv-g-lite` 约 64 KB、`html2canvas` 约 49 KB）已经 `React.lazy`，
  但首屏就会挂载；改成自绘 SVG 大约再省 113 KB，代价是图表细节要自己实现（有视觉回归风险，暂未做）。
- `umi.js` 内部主要是 React/ReactDOM 与 umi runtime，继续拆包收益有限。
- 国内访问速度的主要变量在「Cloudflare 免费版边缘 → 国内客户端」这一段链路，而非包体本身。
