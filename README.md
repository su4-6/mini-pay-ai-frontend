# MiniPay AI Frontend

MiniPay AI（移动端智能体名：**米灵**）的前端 Monorepo。包含三套 Web 控制台与一个 Android 消费者端：

| 应用 | 入口路径 | 技术栈 | 线上地址 |
| --- | --- | --- | --- |
| `apps/admin-web` | `/`（根路径） | React 18 + Umi 4 + Ant Design 5 | https://admin.su46proj.site |
| `apps/ops-web` | `/ops/` | 同上 | https://ops.su46proj.site/ops/ |
| `apps/merchant-web` | `/merchant/` | 同上 | https://merchant.su46proj.site/merchant/ |
| `android` | — | Kotlin + Jetpack Compose | APK：https://download.su46proj.site/downloads/minipay-latest.apk |

> 三个控制台是**同一套设计语言、三套独立构建**：`admin-web` 构建在站点根路径，`ops-web` / `merchant-web` 构建在子路径
> （Umi 的 `base` + `publicPath`），因此网关侧必须先剥前缀再交给静态容器，否则 JS/CSS 会 404 白屏。
> 项目开发、测试与安全要求见 [项目规范](./docs/PROJECT_STANDARDS.md)。

## 仓库结构

| 路径 | 内容 |
| --- | --- |
| `apps/admin-web` · `apps/ops-web` · `apps/merchant-web` | 三套控制台（Umi 4 + Ant Design 5），各自独立构建 |
| `packages/` | 共享包：`api-client`（请求封装）、`api-contracts`（DTO 类型）、`design-tokens`（设计变量）、`ui-desktop`/`ui-mobile`、`shared` |
| `android/` | 消费者端 Android 工程（Kotlin + Compose + 高德 SDK） |
| `docker/` | 静态镜像定义 [k3s-web.Dockerfile](./docker/k3s-web.Dockerfile) 与镜像内 nginx 配置 |
| `e2e/` | Playwright 冒烟用例（mock 后端响应，校验运营端关键页面） |
| `integrations/yshop/` | YShop 外卖后台与 H5 源码，外卖镜像的构建来源（见 [integrations/README.md](./integrations/README.md)） |
| `docs/` | PRD、前端系统分析与设计、Android 需求、原型图与工程规范 |
| `scripts/` | 本地开发与真机运行辅助脚本 |

> 部署路径只有 K3s：早期那套单机 docker-compose 预览（根 `Dockerfile`、`compose.server.yaml`、
> `docker/{merchant,ops,admin}-nginx.conf`、`scripts/deploy-server.sh`）随迁移到 K3s 一并删除，
> 需要时从 Git 历史取回；镜像统一由 `docker/k3s-web.Dockerfile` 构建。

## 线上演示账号

| 端 | 地址 | 账号 | 密码 |
| --- | --- | --- | --- |
| 运营平台 | https://ops.su46proj.site/ops/ | `13800138000` | `MiniPay@123456` |
| 商户平台 | https://merchant.su46proj.site/merchant/ | `13900000009` | `MiniPay@123456` |
| 系统管理平台 | https://admin.su46proj.site/ | `13800138002` | `MiniPay@123456` |
| Android App | 同上 APK 链接 | 任意演示手机号 + 短信验证码 `123456` | — |

登录页有图形验证码；App 端固定演示验证码为 `123456`。

> 权限边界：系统管理平台只对 `system_super_admin` / `system_account_admin` / `system_auditor` 开放；
> 运营账号（`platform_admin`）访问管理端会被拒绝，前端会显示一张纯中文的「当前账号没有管理端权限」说明页，
> 而不是把后端的 403 显示成「加载失败」。

## 常用命令

```powershell
pnpm install
pnpm verify                     # lint + typecheck + test + build
pnpm --filter @minipay/ops-web dev
pnpm --filter @minipay/merchant-web build
```

## 构建期环境变量

Umi 的 `define` 会在**构建期**把下列变量烤进产物，改完必须重新构建（不是运行时变量）：

| 变量 | 用途 | 生产取值 |
| --- | --- | --- |
| `MINIPAY_DEPLOY_BASE` | 应用基路径（不设时生产默认 `/ops/`、`/merchant/`） | 按应用 |
| `AMAP_KEY` / `AMAP_SECURITY_CODE` | 高德 Web 端 JS Key 与安全密钥（jscode）。**两者必须成对**：只给 Key 不给 jscode，地图能出但搜索/逆地理会失败 | `05ccd000…ca9e` / `5998e7a6…9547` |
| `OPS_WEB_PUBLIC_URL` / `MERCHANT_WEB_PUBLIC_URL` / `ADMIN_WEB_PUBLIC_URL` | 登录页「切换门户」链接 | 对应的线上域名 |

```powershell
$env:AMAP_KEY='<Web JS Key>'; $env:AMAP_SECURITY_CODE='<jscode>'
pnpm --filter @minipay/ops-web build
```

高德 SDK 是**按需异步加载**的：只有地图组件（商户“经营位置（地图选点）”）真正出现时才注入 `webapi.amap.com`，
登录页与普通列表页不会为它付出首屏成本。地图搜索做了两级兜底：网络类失败自动重试一次，
仍失败则回退地理编码（`Geocoder.getLocation`），避免偶发网络抖动被误报成「Key 配置错误」。

## 镜像与部署

三个应用使用同一份静态镜像定义 [docker/k3s-web.Dockerfile](./docker/k3s-web.Dockerfile)（`nginx:1.27-alpine`，监听 8080）：

```bash
docker build -f docker/k3s-web.Dockerfile --build-arg APP=ops-web -t suqihang/ops-web:<tag> .
```

镜像内的 nginx 行为（[docker/k3s-static-nginx.conf](./docker/k3s-static-nginx.conf)）：

- HTML 外壳 `Cache-Control: no-cache`（保证前端修复能上线），hash 资源 `public, max-age=604800, immutable`
- `try_files $uri $uri/ /index.html` 支撑 SPA 路由

部署到 K3s 的清单、网关路由和回滚方式见后端仓库 [deploy/k3s/README.md](../mini-pay-ai-backend/deploy/k3s/README.md)。

## 性能实践（含实测）

| 手段 | 说明 |
| --- | --- |
| `codeSplitting: { jsStrategy: 'granularChunks' }` | 按 npm 包拆分 vendor，避免单个巨型 chunk |
| `jsMinifier: 'terser'` | 比 esbuild 压缩率更高：`umi.js` 223→**189 KB**、`237.*.async.js` 324→**299 KB**（gzip） |
| `mfsu: false` | 生产构建不做依赖预打包，产物稳定可复现 |
| 按需加载高德 SDK | 首屏不再拉高德脚本 |
| logo 256×256（8 KB） | 原 1024×1024（45 KB）；**未哈希资源换文件后必须清理 CDN 缓存** |
| hash 文件名 + `immutable` | 配合 Cloudflare 边缘缓存，热加载几乎 0 字节回源 |

实测（无头浏览器冷/热加载，`ops/login`）：首屏传输 **414 KB → 325 KB（−21%）**，其中 JS 358→310 KB、图片 48→11 KB；
热加载 0.44–0.50 s。复现方式：后端仓库 `_codex_digest/accept/cdp-perf.mjs`（本地验收脚本，不入库）。

## Android

Debug 默认连接本机服务，可在不提交版本库的 `android/local.properties` 覆盖；
Release 必须提供全部 HTTPS 地址与签名属性，详见 [android/README.md](./android/README.md)。

当前线上包：`versionName 0.1.5` / `versionCode 6`（`com.minipay.mobile`），
40,254,032 B，已内置高德 Android Key（`com.amap.api.v2.apikey`）。

主页定位与实时天气使用高德 Android SDK：请在不提交版本库的 `android/local.properties` 配置
`MINIPAY_AMAP_ANDROID_KEY=<Android Key>`；该 Key 需要绑定包名 `com.minipay.mobile` 与对应签名 SHA1。
未配置时应用仍可运行，地图相关界面会给出可重试的降级提示。

Food（外卖）WebView 默认不加载远程页面；启用前必须配置受信任的 HTTPS 域名并完成后端授权契约。
