# MiniPay AI 前端项目规范

> 适用范围：`MiniPay-AI-Frontend` 全部 Web 应用、共享包与 Android 工程。本文以《MiniPay AI 智能支付平台 PRD v1.3》为产品基线；当 PRD、接口契约和本文冲突时，按 **PRD → 版本化契约 → 本文** 的优先级处理。

## 1. 产品与交付边界

- 当前 P0 是注册/登录、账户、钱包、转账、付款确认、账单、AI Talk 与运营/商户支付后台；前端不得擅自把原型占位当作已完成业务。
- 外卖商家、菜单、购物车、履约、外卖订单与外卖授权的实际集成均为 P1。不得在 MiniPay 中重建这些业务或复制第三方品牌资产。
- Consumer H5 和 Android App 共用身份、账户、转账单与账单的后端事实来源；同一业务状态不得在端侧重复定义。
- Android 只允许以受控 WebView 承载未来的 UniApp 外卖 H5。Consumer H5 不嵌入 Android，付款页面始终由 Android 原生实现。

## 2. 目录、依赖与架构

```text
apps/                 可独立构建的产品应用
  consumer-h5/        C 端 React + Umi + Ant Design Mobile
  ops-web/            运营端 React + Umi + Ant Design
  merchant-web/       商户端 React + Umi + Ant Design
packages/             不含业务页面的共享能力
  design-tokens/      颜色、间距、字体等设计令牌
  api-contracts/      OpenAPI 派生或手工维护的版本化契约
  api-client/         统一请求、错误与请求标识处理
  shared/             无副作用的公共工具
  ui-desktop|mobile/  可复用的纯展示组件
android/              Kotlin + Jetpack Compose 原生容器
docs/                 契约、规范与架构决策文档
```

- 应用只能依赖 `packages/*` 的公开导出，不可跨应用导入源码。
- 共享包不依赖具体页面、路由或业务 API；业务模型、页面状态与路由留在各自应用内。
- Web 统一使用 React、TypeScript、Umi、Less/CSS Modules、Zustand、Day.js、TanStack Query 与 Umi Request。不得引入 Moment，也不得新建第二套 Axios 请求层。
- Android 统一使用 Kotlin、Jetpack Compose、Navigation、Hilt、OkHttp、Kotlin Serialization、AndroidX WebKit 与 Keystore；新增依赖必须说明用途、许可证和替代方案。

## 3. Web 编码规范

- TypeScript 必须开启严格模式；禁止 `any`、隐式类型转换和未处理的 Promise。接口响应先经契约解析，再进入视图。
- 页面组件负责组合与交互；可复用视图放入 `packages/ui-*`；请求、错误映射和认证头统一在 `api-client` 与 Umi Request 配置中维护。
- Zustand 仅保存客户端/短期交互状态，例如 UI 抽屉、草稿与会话视图；服务端数据、加载和失效由 TanStack Query 管理。
- 所有写操作必须带 `X-Request-Id` 与后端要求的幂等键；成功后按精确 query key 失效，禁止无差别刷新全部缓存。
- 组件使用语义化 HTML，控件具备可见焦点、明确名称和不少于 44px 的触控区域。Consumer H5 必须在 375px 与 768px 下无横向滚动。
- 视觉实现以 `design-tokens` 为唯一颜色和间距来源；不得在业务页面散落新的品牌色、阴影或字号常量。

## 4. 支付、AI 与数据安全

- 金额在前端仅作展示和输入校验；不得使用浮点数计算金额。以服务端返回的“分”为准，前端不可篡改权威金额。
- 支付密码、令牌、确认令牌、完整身份证明和敏感账户信息不得写入日志、埋点、Query 缓存、Zustand、浏览器持久化或 AI 上下文。
- AI Talk 只能请求已登记的受控工具；所有转账和付款必须落入 `prepare → authenticate → confirm` 的原生/后端流程，并由用户明确确认。
- 错误提示面向用户时使用安全、可行动的文案；诊断信息使用 `requestId`/`traceId` 关联，不能暴露堆栈、令牌或内部地址。

## 5. Android 与 Food WebView 规范

- WebView 默认不加载任何远程页面。只有配置精确的 HTTPS Origin、完成后端授权契约并通过安全评审后才允许启用。
- 禁止 `addJavascriptInterface`、混合内容、文件访问和任意跳转；导航、重定向及桥接消息均必须验证精确 Origin。
- 桥接仅遵循 [Food H5 Bridge v1](./food-bridge-v1.md)：允许授权状态、关闭页面、请求原生付款及相应结果。未知消息一律拒绝。
- Token、刷新令牌、支付密码、支付授权令牌和权威金额不得进入 H5 或桥接负载。原生付款页必须自行向后端获取并校验付款意图。

## 6. API、状态与异常处理

- 契约变更先更新 `api-contracts` 与文档，再更新客户端和页面；破坏性变更必须升版本并提供迁移期。
- 后端统一错误格式必须映射为：可重试、需重新认证、需人工确认、不可恢复四类。页面必须覆盖加载、空数据、无权限、网络失败、超时和重试状态。
- 轮询、SSE 或回调引起的状态变化必须按订单/账户精确失效缓存；“处理中”的支付或转账只能查询或等待恢复，不能重新创建相同意图。
- 日志中保留 `requestId`、`traceId`、业务单号和脱敏后的关联标识，不记录密码、令牌、完整手机号或金额以外的敏感明细。

## 7. 测试与交付门禁

提交前至少执行：

```powershell
pnpm verify
cd android
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-22'
.\gradlew.bat :bridge-contract:test --no-daemon
```

- 新增请求客户端、缓存失效、错误映射或桥接规则时，必须补充对应单元测试。
- 合并前，三个 Web 应用必须通过 Lint、类型检查与生产构建；涉及路由或移动端布局时，补充浏览器 smoke test。
- Android 变更至少验证 Compose 路由和桥接策略；涉及 WebView 时验证白名单、未知消息拒绝和敏感字段拒绝。
- 任何改变付款、授权、金额、身份或外部集成边界的改动，必须由产品与安全负责人复核后才可合并。

## 8. Git 与文档规范

- 提交格式采用 Conventional Commits：`feat:`、`fix:`、`docs:`、`refactor:`、`test:`、`chore:`。一次提交只表达一个可回滚意图。
- 不提交 `node_modules`、Umi 生成物、Gradle 构建产物、本地 SDK 配置、密钥、令牌或真实用户数据。
- 影响架构、接口、安全边界或接入流程的改动，必须同步更新 `docs/`；可复用且影响多个端的决策应补充 ADR。
- PR/合并说明至少包含：变更范围、PRD 对应项、验证命令与结果、风险/回滚方案，以及是否涉及敏感数据或外部系统。

## 9. 例外处理

确有必要偏离本规范时，需在变更说明中写明原因、影响范围、替代控制措施、到期时间和负责人。涉及支付、身份、授权、金额或 WebView 安全边界的例外，不得仅凭代码注释批准。
