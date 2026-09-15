# MiniPay AI · 前端

MiniPay AI 是一套**支付 + 生活服务（点餐外卖 + AI 助手「米灵」）**的全栈系统。本仓库是它的前端 Monorepo：
三套 Web 控制台（运营 / 商户 / 系统管理）和一个 Android 消费者端。

- **在线体验**：运营平台 [ops.su46proj.site/ops](https://ops.su46proj.site/ops/) · 商户平台 [merchant.su46proj.site/merchant](https://merchant.su46proj.site/merchant/) · 系统管理 [admin.su46proj.site](https://admin.su46proj.site/)
- **Android 安装包**：[download.su46proj.site/downloads/minipay-latest.apk](https://download.su46proj.site/downloads/minipay-latest.apk)
- **后端服务与部署**：[mini-pay-ai-backend](https://github.com/su4-6/mini-pay-ai-backend)

演示账号密码统一为 `MiniPay@123456`：运营 `13800138000`、商户 `13900000009`、系统管理 `13800138002`；
App 用任意演示手机号 + 短信验证码 `123456`。演示环境数据是构造的，请不要填写真实个人信息。

## 界面一览

<table>
  <tr>
    <td width="62%"><img src="./docs/prototypes/%E8%BF%90%E8%90%A5%E4%B8%BB%E9%A1%B5.png" alt="运营平台 · 运营总览"><br><sub>运营平台 · 运营总览（实时交易趋势与待处理告警）</sub></td>
    <td width="38%"><img src="./docs/prototypes/%E7%B1%B3%E7%81%B5-%E4%B8%BB%E9%A1%B5.png" alt="Android · 米灵"><br><sub>Android · 米灵（AI 助手首页）</sub></td>
  </tr>
  <tr>
    <td colspan="2"><img src="./docs/prototypes/%E5%95%86%E6%88%B7%E4%B8%BB%E9%A1%B5.png" alt="商户平台 · 商户门户"><br><sub>商户平台 · 商户门户（收款、结算与订单概览）</sub></td>
  </tr>
</table>

更多页面见 [docs/prototypes](./docs/prototypes)（22 张，覆盖三端与 App）。

## 四端各自的职责

| 应用 | 使用者 | 主要内容 |
| --- | --- | --- |
| `apps/ops-web` | 平台运营 | 运营总览、商户与入驻审核、支付/退款/转账/充值/提现订单、外卖订单、收款记录、通知中心、登录审计 |
| `apps/merchant-web` | 商户 | 商户门户、门店资料与经营位置（地图选点）、收款与结算、订单查询、退款处理、应用与密钥管理 |
| `apps/admin-web` | 系统管理员 | 账号与角色、系统审计、服务健康聚合、通知与邮件配置 |
| `android` | C 端用户 | AI 助手「米灵」流式对话、钱包与账单、转账收款、支付密码、订单与外卖入口、个人资料 |

## 技术栈

| 层 | 选型 |
| --- | --- |
| Web | React 18 · Umi 4 · Ant Design 5 · TypeScript 5 |
| 工程 | pnpm workspace 单仓多包（`apps/*` + `packages/*`）、ESLint + Prettier + Playwright |
| 地图 | 高德地图 JS SDK（Web 选点/搜索）、高德 Android SDK（定位与天气） |
| App | Kotlin · Jetpack Compose · 单 Activity 导航 · WebView 桥接外卖 H5 |
| 构建产物 | 三端同构静态镜像（`nginx:1.27-alpine` 托管，监听 8080） |

## 目录结构

| 路径 | 内容 |
| --- | --- |
| `apps/admin-web` · `apps/ops-web` · `apps/merchant-web` | 三套控制台，各自独立构建 |
| `packages/` | 共享包：`api-client`（请求封装与错误语义）、`api-contracts`（DTO 类型）、`design-tokens`（设计变量）、`ui-desktop` / `ui-mobile`、`shared` |
| `android/` | Android 工程与打包说明（[android/README.md](./android/README.md)） |
| `docker/` | 静态镜像定义 [k3s-web.Dockerfile](./docker/k3s-web.Dockerfile) 与镜像内 nginx 配置 |
| `e2e/` | Playwright 冒烟用例（mock 后端响应，覆盖运营端关键页面） |
| `integrations/yshop/` | YShop 外卖后台与 H5 源码，外卖镜像的构建来源（[integrations/README.md](./integrations/README.md)） |
| `docs/` | 需求、系统设计、Android 需求、原型图、工程规范（[docs/README.md](./docs/README.md)） |
| `scripts/` | 本地开发与真机运行辅助脚本 |

三套控制台共用同一套设计语言，但**独立构建**：`admin-web` 构建在站点根路径，`ops-web` / `merchant-web`
构建在子路径（Umi 的 `base` + `publicPath`）。因此网关必须先剥离路径前缀，否则 JS/CSS 会 404。

## 本地开发

需要 Node 20+ 与 pnpm 10：

```bash
pnpm install
pnpm --filter @minipay/ops-web dev        # 运营平台，默认 http://127.0.0.1:8000
pnpm --filter @minipay/merchant-web dev   # 商户平台
pnpm --filter @minipay/admin-web dev      # 系统管理平台
pnpm verify                               # lint + typecheck + test + build
pnpm e2e                                  # Playwright 冒烟（会自动构建并起静态服务）
```

登录依赖后端身份服务，本地联调步骤见后端仓库的 [RUNBOOK.md](https://github.com/su4-6/mini-pay-ai-backend/blob/main/RUNBOOK.md)。

### 构建期环境变量

Umi 会把下列变量**在构建期写进产物**，改完必须重新构建：

| 变量 | 用途 | 说明 |
| --- | --- | --- |
| `MINIPAY_DEPLOY_BASE` | 应用基路径 | 生产默认 `/ops/`、`/merchant/`；`admin-web` 在根路径 |
| `AMAP_KEY` · `AMAP_SECURITY_CODE` | 高德 Web JS Key 与安全密钥 | **必须成对**，只配 Key 不配 jscode 时地图能出、搜索会失败 |
| `OPS_WEB_PUBLIC_URL` · `MERCHANT_WEB_PUBLIC_URL` · `ADMIN_WEB_PUBLIC_URL` | 登录页「切换门户」链接 | 不设置时按当前域名推导 |

```bash
AMAP_KEY=<Web JS Key> AMAP_SECURITY_CODE=<jscode> pnpm --filter @minipay/ops-web build
```

## 构建与部署

三个应用共用同一个静态镜像定义，通过 `APP` 参数选择构建目标：

```bash
docker build -f docker/k3s-web.Dockerfile --build-arg APP=ops-web -t <registry>/ops-web:<tag> .
```

镜像内 nginx 负责 SPA 回退（`try_files $uri $uri/ /index.html`）与缓存策略：HTML 外壳不缓存，
带 hash 的静态资源长缓存（`immutable`）。集群清单、网关路由与回滚方式见后端仓库
[deploy/k3s/README.md](https://github.com/su4-6/mini-pay-ai-backend/blob/main/deploy/k3s/README.md)。

Android 的 Debug / Release 打包参数（含签名与高德 Key）见 [android/README.md](./android/README.md)。

## 工程约定

- 请求统一走 `packages/api-client`，错误按后端的 Problem Details（`code` + `requestId`）转成前端可读文案，不在页面里直接拼 `fetch`。
- 页面级权限用 `AuthGate` + 路由清单统一声明，避免每个页面各写一套判断。
- 金额一律以「分」传输与展示（整数运算），时间统一按 ISO-8601 解析后本地化。
- 新增页面必须补 `navigation` 注册与最基础的渲染测试；提交前跑 `pnpm verify`。

完整规范见 [docs/PROJECT_STANDARDS.md](./docs/PROJECT_STANDARDS.md)。

## 联系

问题或建议：`su_qihang@163.com`
