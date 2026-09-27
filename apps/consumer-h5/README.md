# @minipay/consumer-h5

MiniPay AI 的**线上 C 端**（H5）。Android App 从服务器下线后，本应用取代它作为主要演示端。

- 独立域名根路径部署：`https://app.su46proj.site/`（Umi `base` / `publicPath` = `/`，**不是子路径**）
- 接口同源：网关把 `/api/**` 路由到 `consumer-bff`，前端不做任何前缀拼接
- 构建产物：`apps/consumer-h5/dist/`（共享镜像 `docker/k3s-web.Dockerfile` 直接 `COPY apps/${APP}/dist/`）
- 不新增/修改 `docker/` 下的文件：静态服务复用共享的 `docker/k3s-static-nginx.conf`（8080、`/healthz`、SPA 兜底、HTML no-cache + 带哈希资源 immutable）

## 1. 本地启动

```powershell
# 仓库根目录
pnpm install
pnpm --filter @minipay/consumer-h5 dev     # http://localhost:8003
```

开发服务器把 `/api/**` 反向代理到 `consumer-bff`，默认目标 `http://localhost:8087`
（见 `backend/services/consumer-bff/src/main/resources/application.yml` 的 `SERVER_PORT`）。
需要改目标时：

```powershell
$env:CONSUMER_BFF_PROXY_TARGET = 'http://localhost:8087'
pnpm --filter @minipay/consumer-h5 dev
```

> **本地登录前置条件**：`consumer-bff` 默认 `SESSION_COOKIE_SECURE=true`，
> 在 `http://localhost` 上浏览器不会保存会话 Cookie。本地联调需把该环境变量设为 `false`
> （或经 https 反代访问）。这是后端配置，前端不做规避。

## 2. 演示验证码

- 手机号规则：`^1[3-9]\d{9}$`
- 短信验证码固定演示码：**`123456`**
- 若 `POST /api/v1/session/sms` 的 202 响应带 `demoCode`，登录页展示后端返回值（后端优先）
- 未注册手机号验证通过后由后端自动建户；协议勾选为占位（不外链、不落库）

## 3. 接口前缀与契约

接口前缀固定 `/api/v1`，同源直连，**不加任何前缀**。字段与路径以 consumer-bff 契约为准。

| 能力 | 方法与路径 |
| --- | --- |
| CSRF | `GET /api/v1/csrf` |
| 下发短信 | `POST /api/v1/session/sms` `{mobile}` → 202 `{challengeId, expiresAt, demoCode?}` |
| 登录 | `POST /api/v1/session` `{mobile, challengeId, code}` → `{userId, phone, displayName, payPasswordSet, onboardingRequired, realNameStatus, realNameVerified}` |
| 会话查询 | `GET /api/v1/session` → `{authenticated:false}` 或 `{authenticated:true, ...}` |
| 登出 | `DELETE /api/v1/session` → 204 |
| 设置支付密码 | `POST /api/v1/pay-password` `{paymentPassword}` |
| 钱包 | `GET /api/v1/wallet` |
| 账单 | `GET /api/v1/wallet/bills?cursor=&limit=` |
| 收款码 | `GET /api/v1/collection-code` |
| 银行卡（P1） | `GET /api/v1/bank-cards` |
| 转账准备 | `POST /api/v1/transfers/prepare` `{payeeIdentifier, amountFen, remark?}` → `{transferIntentId, payeeMasked, amountFen, expiresAt}` |
| 转账确认 | `POST /api/v1/transfers/{intentId}/confirm` `{amountFen, paymentPassword}` → `{transferNo, status}` |
| 取消转账意图 | `DELETE /api/v1/transfers/{intentId}` |
| 转账记录 | `GET /api/v1/transfers?cursor=&limit=` |
| 转账单查询 | `GET /api/v1/transfers/{transferNo}` |
| 扫码付款·识别 | `POST /api/v1/payments/scan` `{deepLink｜merchantToken}` → 商户码 `{type:"MERCHANT_COLLECTION", resolutionId, merchantName, ...}`；个人码只有收款人展示信息 |
| 扫码付款·创建支付单 | `POST /api/v1/payments/prepare` `{resolutionId, amountFen}` → `{paymentOrderId, paymentOrderNo, amountFen, expiresAt}` |
| 扫码付款·确认 | `POST /api/v1/payments/{paymentOrderId}/confirm` `{amountFen, paymentPassword}` → `{paymentOrderNo, status, failureCode?}` |
| AI 会话 | `GET｜POST /api/v1/ai/conversations` |
| AI 历史消息 | `GET /api/v1/ai/conversations/{id}/messages` |
| AI 发消息 | `POST /api/v1/ai/conversations/{id}/messages` `{content, clientMessageId?}` → `{runId}` |
| AI 事件流 | SSE `GET /api/v1/ai/runs/{runId}/events` |

## 4. 页面 ↔ 接口映射

| 路由 | 页面 | 调用 |
| --- | --- | --- |
| `/login` | 短信登录 | 挂载先 `GET /csrf`；`POST /session/sms`；`POST /session`；成功后精确写入并失效 `['session']` |
| `/` | 米灵 AI 对话（主 Tab） | `GET /ai/conversations`、`POST /ai/conversations`、`GET /ai/conversations/{id}/messages`、`POST /ai/conversations/{id}/messages`、SSE `GET /ai/runs/{runId}/events` |
| `/wallet` | 钱包余额 + 最近账单 | `GET /wallet`、`GET /wallet/bills?limit=5` |
| `/bills` | 账单列表（只读游标分页） | `GET /wallet/bills?cursor=&limit=20`（`useInfiniteQuery` + `InfiniteScroll`） |
| `/collect` | 收款码展示 | `GET /collection-code` |
| `/transfer` | 转账填单 → 确认（两步同页） | `POST /transfers/prepare`、`POST /transfers/{intentId}/confirm`（带 prepare 返回的 `amountFen`）、`DELETE /transfers/{intentId}` |
| `/transfer/result?transferNo=` | 转账结果页 | `GET /transfers/{transferNo}`（处理中 3s 轮询，最多 60s） |
| `/transfers` | 转账记录 | `GET /transfers?cursor=&limit=20` |
| `/transfers/detail?transferNo=` | 转账详情 + 恢复查询 | `GET /transfers/{transferNo}` |
| `/pay` | 扫码付款（识别 → 填金额 → 确认，三步同页） | `POST /payments/scan`、`POST /payments/prepare`、`POST /payments/{paymentOrderId}/confirm`（带 prepare 返回的 `amountFen`） |
| `/pay-password` | 设置支付密码（未设置时引导） | `POST /pay-password`；成功后失效 `['session']` |
| `/me` | 我的 | `GET /session`（复用缓存）、`DELETE /session` |

**写操作后的精确失效**（`src/query/keys.ts`）：

- 转账确认成功 → `['wallet']`（余额+账单同族）、`['transfers']`
- 扫码付款确认成功 → `['wallet']`（余额+账单同族）、`['transfers']`
- AI 发送/流结束 → `['ai','conversations',{id},'messages']`、`['ai','conversations']`
- 登录/设置支付密码/登出 → `['session']`（登出额外 `queryClient.clear()` 并清空 CSRF 与内存态）

## 5. CSRF / SSE / 支付密码的实现方式

### CSRF

- 令牌只存在**内存**（`src/services/http.ts` 模块级变量），不写 localStorage / sessionStorage / Query 缓存
- 所有非 GET/HEAD/OPTIONS 请求由 `httpRequest` 自动 `await getCsrfToken()` 后带上 `headerName` 指定的头
- **登录前**（登录页挂载即拉取）与**登录后**（`POST /session` 成功、`DELETE /session` 后立即 `clearCsrfToken()`）都会重新获取
- 并发请求共享同一个在途 Promise，避免重复打 `/csrf`
- 仅当服务端以 `403` + `code` 含 `CSRF` 明确拒绝时，才刷新令牌并重放**一次**；这类请求在进入业务逻辑前已被拒绝，不存在重复扣款风险。其余失败一律上抛，写操作绝不自动重试
- 每个请求都带 `X-Request-Id`，Problem Details 的 `requestId` 缺失时回退到响应头/本地请求号，UI 展示时保留

### SSE（米灵流式回复）

- 调用顺序对齐 Android `MilingAiViewModel.submit` / `observeRun`：
  `POST /ai/conversations/{id}/messages` → 拿 `runId` → SSE `GET /ai/runs/{runId}/events`
- 用 `fetch` + `ReadableStream` 手写帧解析（`src/services/sse.ts`），不是第二套请求层：
  Umi Request 基于 axios，浏览器适配器会整体读完响应体，无法逐帧暴露 SSE；业务读写仍全部走 Umi Request
- 事件信封：`{id, type, payload}`；处理 `message.delta`（追加 `payload.text`）、
  `message.completed`、`task.error` / `security.error` / `run.failed`（记失败文案）、
  `stream.completed`（终态收尾）；`heartbeat` / `ping` 被忽略
- **断点续传**：事件 `id` 单调推进时更新本地游标，重连用 `Last-Event-ID`；切换会话后重新进入会按 store 中的 `streamRunId` + `lastEventId` 续传
- **断线**：非终态断开按 500/1000/2000ms 退避重连，最多 3 次；已收到明确失败事件时不再重连，直接以该事件文案收尾
- **结束/失败/空态**：`stream.completed` 才结束并失效消息缓存；失败展示可读文案（不伪造成功）；
  无会话时展示引导与建议提问；`stream.completed` 后重新拉取历史，以服务端为准
- 只有 `streamRunId` 属于当前会话时才会续传，避免串会话

### 支付密码

- 组件：`src/components/PayPasswordField.tsx`，`type="password"` + `inputMode="numeric"` + `autoComplete="off"`，最多 6 位数字
- 只通过受控 props 在内存中流转；**不落 localStorage / sessionStorage / URL / 日志 / Query 缓存 / Zustand**
- 提交成功或失败后立即 `setPaymentPassword('')` 清空；页面刷新即丢失
- 转账为资金操作，必须经过：填单 → 服务端 `prepare` 生成意图 → 展示收款方与金额 → 用户再次 `Dialog.confirm` 二次确认 → `confirm` 提交支付密码与**意图金额**
- **confirm 必须带 `amountFen`**：consumer-bff 会把该值透传给身份服务签发「限定金额」的一次性支付授权令牌
  （绑定 `intentId` + 金额，防篡改）。因此：
  - 金额来源**只能是** `prepareTransfer` 返回的 `PreparedTransfer.amountFen`（服务端权威意图值），
    绝不能用输入框里用户可改的 `amountInput`；二者不一致时以服务端返回值为准；
  - 全程整数「分」，不做四舍五入、不做浮点换算；
  - 本地硬校验（`assertAuthorizedAmount`）：金额非正整数（含 `0`/小数/负数/NaN）直接抛出
    `TRANSFER_AMOUNT_REQUIRED` 并**不发请求**，杜绝「静默签发 0 分授权」；
  - 契约由 `src/services/transfers.test.ts` 锁定（断言请求体恰为 `{amountFen, paymentPassword}`、
    金额等于 prepare 的意图值、且不含任何用户可改的金额字段）
- 支付密码未设置时：钱包页横幅、转账页提示并阻断提交，均引导到 `/pay-password`；服务端返回 `PAY_PASSWORD_NOT_SET` 时同样引导
- 转账意图（含收款方与金额）只放在内存 Zustand（`src/stores/transfer-intent.ts`），刷新即丢弃；
  已提交的转账通过 `GET /api/v1/transfers/{transferNo}` 查询恢复，**不重复创建**
- **扫码付款同构且更严**：`/pay` 也走「识别 → 服务端创建支付单 → 支付密码换一次性授权令牌 → 确认」，
  confirm 的金额同样只能取 `prepareMerchantPayment` 返回的 `PreparedPayment.amountFen`（服务端权威值），
  本地硬校验与转账一致；`src/services/payments.test.ts` 锁定了三个请求体的字段集合。
  个人收款码没有 `resolutionId`，页面直接说明「请改用转账」，不会发出 prepare 请求。
  `paymentMethod`（余额支付）与账单摘要（「扫码付款」）由 consumer-bff 固定，前端不参与拼装，
  避免浏览器输入被写进商户/运营端可见的账单字段。

## 6. 状态与目录划分

```text
src/
  app.tsx                QueryClient + Umi Request 全局配置 + 401 会话失效联动
  global.less            design-tokens 变量、重置、焦点样式、44px 触控下限
  components/            复用视图（AppShell / AuthGate / AsyncState / Card / AmountText /
                         PayPasswordField / ProblemNotice / InlineNotice / TransferResultPanel /
                         chat/{ConversationDrawer,MessageList,ChatComposer}）
  constants/routes.ts    路由常量
  hooks/                 useSession / useAiStream / useNow
  pages/                 每个路由一个文件 + 同名 *.module.less
  query/keys.ts          TanStack Query key 工厂
  services/              http(CSRF/Problem) / session / wallet / transfers / payments / ai / sse / parsers / problem
  stores/                Zustand：chat（会话视图、草稿、流式缓冲）、transfer-intent（转账意图）
  types/consumer.ts      C 端业务契约（应用内私有，不进 packages）
  utils/                 money（整数分）/ datetime / validators / redirect / transfer-status
```

- 服务端数据全部由 TanStack Query 管理；Zustand 只放短期 UI 状态
- 请求层只有一套：Umi Request（`@umijs/max` 的 `request`），由 `src/services/http.ts` 统一补 CSRF、`X-Request-Id` 与 Problem Details 映射；未引入 axios 或第二套请求层
- 金额一律整数「分」；`src/utils/money.ts` 用字符串+整数解析与格式化，**不出现浮点金额运算**
- 视觉常量全部来自 `@minipay/design-tokens`（`global.less` 通过 `~@minipay/design-tokens/less` 引入）
- 触控目标 ≥44px、语义化标签（`nav`/`section`/`dl`/`dt`/`dd`/`label`）、`:focus-visible` 可见焦点；375px 与 768px 无横向滚动（`html,body{overflow-x:hidden}`，768px 起居中限宽）

## 7. 验证命令与结果

```powershell
pnpm install
pnpm --filter @minipay/consumer-h5 typecheck
pnpm --filter @minipay/consumer-h5 build
pnpm exec eslint apps/consumer-h5/src --ext .ts,.tsx
pnpm --filter @minipay/consumer-h5 test
```

最近一次实际执行结果（Windows / pnpm 10.34.5 / Node 24）：

| 命令 | 结果 |
| --- | --- |
| `pnpm install` | 成功；新增 `antd-mobile@5.43.0`、`antd-mobile-icons@0.3.0`、`qrcode.react@4.2.0`（均零运行时依赖，MIT/ISC）。存在仓库既有的 umi/dva/vitest peer 警告，与本次改动无关 |
| `pnpm --filter @minipay/consumer-h5 typecheck` | 通过（`max setup && tsc -p tsconfig.json`，strict，0 error） |
| `pnpm --filter @minipay/consumer-h5 build` | 通过，产物在 `apps/consumer-h5/dist/`，`index.html` 引用 `/umi.*.js`（确认 base=`/`） |
| `pnpm exec eslint apps/consumer-h5/src --ext .ts,.tsx` | 通过，0 error 0 warning |
| `pnpm --filter @minipay/consumer-h5 test` | 通过，5 个文件 37 个用例 |

根目录脚本已把 `consumer-h5` 加入 `build` / `typecheck` / `lint` / `test` 的 filter 列表。

> 注意：根目录 `pnpm test` 目前会在 **ops-web** 阶段失败（`Error: Test timed out in 5000ms`）。
> 这是既有问题、与本次改动无关：单独运行 `vitest run src/pages/login.test.tsx --testTimeout=60000`
> 时同样的用例全部通过（单个用例耗时 1–2.4s，整包并行时被本机负载挤爆 5s 默认超时），
> 且两次运行的失败数不同（11 → 5），属于环境/超时抖动而非确定性回归。
> `pnpm verify` 因此会在 `test` 环节中断；本次交付以 `consumer-h5` 自身的四条命令为准。

## 8. 已知限制与风险

1. **后端字段命名未定稿的部分采用宽松解析**。契约只给了路径、没给字段名的端
   （`/wallet`、`/wallet/bills`、`/collection-code`、`/bank-cards`、`/transfers` 列表与详情），
   `src/services/parsers.ts` 按「`...Fen` 优先，兼容 `...Cent`」和「`items`+`nextCursor`、`items`+`page/size/total` 双信封」读取。
   宽松读取只做字段映射，绝不改写金额单位。若后端最终字段名不同，只需调整 `services/wallet.ts` /
   `services/transfers.ts` 里的键名列表。
   **注意：该宽松解析不涉及转账授权金额**——`confirm` 用的 `amountFen` 取自 `prepare` 返回的
   `{transferIntentId, payeeMasked, amountFen, expiresAt}`（契约已明确该字段名，解析按精确键名读取），
   并有 `src/services/transfers.test.ts` 锁定请求体；宽松读取只影响转账**列表/详情**的展示字段。
2. **`prepare` 未返回金额时的兜底**：若 `prepare` 响应完全不含金额字段，`parseIntent` 会用本次
   请求的金额渲染确认页（否则页面无法展示金额）。这一兜底不会绕过服务端校验：后端将把
   `amountFen <= 0` 作为强校验拒掉，且授权令牌绑定 intentId + 金额，金额不一致会被上游拒绝。
   建议后端保证 `prepare` 始终回显 `amountFen`（本应用已按精确键名读取）。
3. **分页游标兜底**：服务端未返回 `nextCursor` 时会用最后一条的 `billId`/`transferNo`/`id` 兜底；
   若后端游标语义不是「最后一条 id」，需以后端返回的 `nextCursor` 为准。
4. **转账单金额上限**：前端护栏为 ¥10,000.00（`MAX_AMOUNT_FEN`，对齐 `payment-api-v1.yaml` 的
   `amountCent maximum: 1000000`）。服务端为最终权威，超限会返回 Problem Details 并由页面展示。
5. **转账意图不持久化**：确认页刷新后本地意图丢失，页面会提示重新发起；已提交的转账只能通过
   `GET /transfers/{transferNo}` 查询，符合「不重复创建意图」的要求。
6. **收款码依赖后端返回内容**：`GET /collection-code` 的 `code` 字段用于本地渲染二维码
   （`qrcode.react`，零依赖）；若后端直接返回图片则优先展示 `qrImageUrl`。
   如果后端字段名不同（如 `deepLink`/`payload`/`token`），解析器已做兼容，但仍显示为纯文本兜底。
7. **未实现（按范围要求）**：注册开户与实名（后端 BFF 只读实名状态，没有提交入口，要补需先加
   consumer-bff 端点）、点餐外卖、好友与群聊、语音通话、后台推送；银行卡列表/充值/提现为 P1，本次未开发。
   ~~扫码~~ **已补**（2026-09-27）：`/pay` 增加「扫一扫商户收款码」，见下面第 10 条。
8. **无浏览器端到端验证**：本次只做了类型检查、生产构建、Lint 与单元测试，没有对真实
   `consumer-bff` 做联调（BFF 的会话/钱包/转账端点在本次工作期间仍在实现中），
   因此登录、SSE 流、转账 prepare→confirm 的真实链路尚未跑通验证。
   > 2026-09-27 更新：真实链路已由 `scripts/k3s/acceptance-server.ps1` 在线上跑通
   > （H5 外壳 / CSRF / 短信登录 / 钱包账单 / 米灵 SSE / 转账落账 / 商户扫码付款，21 passed / 0 failed）。
9. `pnpm install` 时 pnpm 10 默认忽略 `esbuild` 等依赖的构建脚本（仓库既有行为），
   本应用的 `vitest`/`max build` 在忽略脚本的情况下仍然正常。
10. **相机扫码（2026-09-27 补充）**：`src/components/ScanCodeButton.tsx` + `src/utils/scan.ts`。
    复用浏览器自带的 `BarcodeDetector`，**不引入任何解码依赖**；只在用户点开时申请摄像头，
    识别到第一帧或关闭浮层立刻 `stop()` 所有轨道。`extractPaymentCode()` 同时接受
    商户深链、裸令牌、带 `token=` 的链接；个人码也解析出令牌，交给后端给出精确提示
    （`SELF_COLLECTION_CODE` 等）。不支持的浏览器（无 `BarcodeDetector`，如 Firefox）
    退化为提示文案，粘贴路径始终可用。单测见 `src/utils/scan.test.ts`。
11. **构建注意**：`.dockerignore` 原先只放行了 `merchant-web`/`ops-web`/`admin-web` 的 `dist`，
    `COPY apps/consumer-h5/dist/` 会报 `not found`；已补 `!apps/consumer-h5/dist` 与
    `!apps/consumer-h5/dist/**`，现在可以直接
    `docker build -f docker/k3s-web.Dockerfile --build-arg APP=consumer-h5 -t suqihang/consumer-web:<tag> .`
    （记得加 `--provenance=false --sbom=false`，否则 k3s 导入后报 `image can't be pulled`）。
12. **⚠️ 2026-09-27 线上「H5 打不开」事故（两个根因，均已修复 + 测试锁定）**：
    - **白屏**：`src/app.tsx` 里 `export const queryClient = ...`。Umi 会把 `app.tsx` 的**每个导出**
      当运行时插件注册，白名单（生成的 `.umi/core/plugin.ts` 的 `getValidKeys()`）里没有 `queryClient`，
      `pluginManager.register()` 的断言直接抛 `register failed, invalid key queryClient .`，
      应用启动中断 → 整页白屏。修法：改为模块私有（页面里用 `useQueryClient()`）。
    - **所有 GET 失败（界面显示「网络不可用」）**：`app.tsx` 的 `request.requestInterceptors` 用了
      umi-request 的 `[url, options]` 元组写法，而生成的 `.umi/plugin-request/request.ts` 底层是 **axios**：
      它会解构成 `{ url: newUrl, options }` 再 `{ ...options, url }`，**丢掉 `method`**，axios 随即在
      `config.method.toUpperCase()` 抛 `Cannot read properties of undefined`，被 `services/http.ts`
      归一化成 `NETWORK_UNAVAILABLE`（连会话查询都挂）。修法：删掉该拦截器（`X-Request-Id`/`Accept`/
      `withCredentials` 已在 transport 逐请求设置），并让 transport 显式 `method ?? 'GET'`。
    - **闸门**：`src/app.test.ts` 断言「导出名 ⊆ Umi 白名单」且「不得出现 `requestInterceptors:` 配置项」。
    - **教训**：此前验收只查「index 可访问 + 资源 200 + API 流程」，**没有浏览器渲染断言**，
      所以白屏能长期存在而无人发现。现已用 Playwright（`channel: 'msedge'`，不必下载浏览器）
      跑真实登录 + 渲染冒烟；做法记录在 `deploy/k3s/README.md`。
    - **线上现状**：`consumer-web` 以 digest 部署修复版，浏览器实测「登录 → 首页」正常、零控制台错误。
