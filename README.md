# MiniPay AI Frontend

MiniPay AI（移动端智能体名：**米灵**）的前端 Monorepo。Android 已接入 AI 会话、SSE 流式任务、钱包与账单卡片、站内转账、沙箱外卖、原生支付确认和长期记忆管理；资金与订单结果始终以后端权威接口为准。

## 应用

项目开发、测试与安全要求见 [项目规范](./docs/PROJECT_STANDARDS.md)。

- `apps/consumer-h5`：消费者 H5，React + Umi + Ant Design Mobile。
- `apps/ops-web`：支付运营后台，React + Umi + Ant Design。
- `apps/merchant-web`：支付商户后台，React + Umi + Ant Design。
- `android`：Kotlin + Jetpack Compose 消费者端；包含米灵原生会话与结构化业务卡片，同时保留传统钱包、账单、扫码和收款页面。

## 常用命令

```powershell
pnpm install
pnpm verify
pnpm --filter @minipay/consumer-h5 dev
```

Android 需使用 Android Studio 或 Gradle 运行。Food WebView 默认不加载远程页面；启用前必须配置受信任的 HTTPS 域名并完成后端授权契约。

Android Debug 默认连接本机服务：Identity `8081`、Payment `8082`、Wallet `8083`、Commerce `8085`、Agent `8086`。可在用户级 Gradle 属性或不提交版本库的 `android/local.properties` 中覆盖：

```properties
MINIPAY_DEBUG_AGENT_BASE_URL=http://127.0.0.1:8086
MINIPAY_DEBUG_COMMERCE_BASE_URL=http://127.0.0.1:8085
```

Release 构建必须提供 HTTPS 的 `MINIPAY_AGENT_BASE_URL` 与 `MINIPAY_COMMERCE_BASE_URL`，并与既有 Identity、Payment、Wallet 地址一并通过构建校验。模型密钥只配置在 Agent 后端，不进入 Android 构建产物。

主页定位与实时天气使用高德 Android SDK。请在用户级 Gradle 属性或不提交版本库的
`android/local.properties` 中配置 `MINIPAY_AMAP_ANDROID_KEY=<Android Key>`；该 Key 需要绑定
`com.minipay.mobile` 以及对应 Debug/Release 签名 SHA1。未配置时应用仍可运行，主页会显示可重试的定位降级状态。
