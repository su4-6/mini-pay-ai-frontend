# MiniPay AI 智能支付平台

# 前端系统分析与设计说明书 v1.7.0

> 本文按《前端系分模版》组织。接口示例为目标契约，必须先落入后端 OpenAPI，再由 `packages/api-contracts` 生成类型；“目标新增”不代表当前仓库已经实现。

# 1. 需求背景

MiniPay AI 是面向作品展示和技术联调的智能支付沙箱。P0 交付 Android 消费者端、商户 Web 和运营 Web，形成登录开户、AI/社交转账、群聊、扫码收款、银行卡充值提现、订单退款及运营排查闭环。所有资产均为沙箱资产，不产生真实资金流。

产品文档文件名为 v1.5.0，但变更记录和正文已经包含 v1.6.0 银行卡/多支付方式及 v1.7.0 群聊规则；本文以 **PRD 正文 v1.7.0** 为唯一产品基线。

## 1.1 项目成员

| 角色 | 成员 | 备注 |
|---|---|---|
| 业务方 | 待填写 | 沙箱演示验收 |
| 产品经理（PD） | 待填写 | PRD 与验收口径负责人 |
| 后端技术 | 待填写 | OpenAPI、事件及联调环境负责人 |
| UED（设计师） | 待填写 | 原型补齐、视觉与交互验收 |
| Android 前端 | 待填写 | Kotlin/Compose |
| Web 前端 | 待填写 | React/Umi/Ant Design |
| 质量 | 待填写 | 功能、安全、资金一致性验收 |

## 1.2 项目文档

| 文档/资源 | 地址或说明 |
|---|---|
| PRD | `frontend/docs/MiniPay-AI-PRD-v1.5.0.md`，内容基线 v1.7.0 |
| 前端仓库 | `https://gitee.com/niukinng/mini-pay-ai-frontend.git` |
| 后端仓库 | `https://gitee.com/niukinng/mini-pay-ai-backend.git` |
| 后端系分 | `MiniPay-AI-后端系统分析与设计-v1.7.0.md` |
| 前端工程规范 | `frontend/docs/PROJECT_STANDARDS.md` |
| 原型 | `frontend/docs/prototypes/`；群聊、登录、安全、扫码、银行卡等页面仍待补图 |
| 当前实现基线 | 前端 `aff01cac73fa8a44a9389b3073642a6e595ed21f`（2026-07-31） |
| 迭代地址 | 待填写 |
| 开发/测试环境 | 待后端提供；Web 本地默认代理 `management-bff:8088` |

# 2. 详细设计

## 2.1 前端迭代目标

1. Android 用 Jetpack Compose 实现 APP-01～APP-34 P0 页面与导航，移除当前启动链路中的外卖授权占位，Consumer H5 不进入本期导航。
2. 商户 Web 实现 MER-01～MER-08；运营 Web 在既有安全会话、菜单壳和登录审计上实现 OPS-02～OPS-08。
3. 统一使用版本化契约、整数分金额、RFC 9457 错误、幂等写请求、精确缓存失效和敏感字段最小化。
4. 所有 P0 页面覆盖加载、空态、失败、无权限；处理中交易只查询恢复，不重复创建。

### 2.1.1 现有实现盘点

| 端/模块 | 当前状态 | 处理结论 |
|---|---|---|
| Android | 仅聊天/外卖授权/WebView/付款占位路由，无真实业务请求 | 保留工程依赖和安全基线；重建 P0 导航与业务分层，外卖路由不暴露 |
| Consumer H5 | 工程骨架，PRD 已明确不在本期 | 不开发、不进入构建发布准入之外的业务验收 |
| 商户 Web | 只有首页占位 | 新增认证壳、路由、页面、服务层和测试 |
| 运营 Web | Session/CSRF、权限门、响应式菜单、退出登录、登录审计已实现 | 复用；修正 PRD 页面编号后新增业务页 |
| 共享 API | 只有 Problem/Session/登录审计类型 | 以后端 OpenAPI 生成完整 DTO；禁止业务页自定义冲突类型 |
| 请求层 | 具备 `X-Request-Id`，运营端携带 Cookie | 增加 Problem 分类、幂等键、Android Token 刷新和取消请求 |

## 2.2 迭代具体描述

### 2.2.1 前端总体架构

```text
Android Compose
├─ core/network        OkHttp、OAuth/PKCE、Problem 映射
├─ core/security       Keystore、支付敏感输入、截图控制
├─ core/model          OpenAPI 生成模型的端侧适配
├─ feature/auth|agent|social|group|scan|wallet|profile
└─ navigation          类型安全路由与 Deep Link

Web Monorepo
├─ apps/merchant-web   商户页面、路由、Query
├─ apps/ops-web        运营页面、路由、Query（复用现有 AuthGate/OpsShell）
├─ packages/api-contracts  OpenAPI 生成类型
├─ packages/api-client     请求、Problem、幂等、requestId、CSRF
└─ packages/ui-desktop     列表、状态页、金额/状态展示组件
```

**状态与缓存规则**

- 服务端数据由 TanStack Query/Android Repository + ViewModel 管理；草稿、抽屉、筛选临时态才进入 Zustand/SavedStateHandle。
- 金额模型使用 `amountCent: Long/number`。Web 仅允许安全整数并用字符串解析输入；不得用浮点数参与结算。
- Token、支付密码、验证码、完整银行卡、Cookie 不进入日志、埋点、持久化 Store 或 AI 上下文。
- 写操作生成 16～128 字符 `Idempotency-Key`；同一业务重试复用原键，不重新生成。
- 202/`PROCESSING` 进入状态查询：前 30 秒 1s、2s、3s、5s 退避；超过 30 秒停止自动轮询并跳账单。

### 2.2.2 Android 登录与安全（APP-01/02/02A）

##### UI&交互

- APP-01：11 位手机号、协议勾选、下一步；输入非法或未勾选时按钮禁用。
- APP-02：6 格验证码，支持系统短信自动填充；60 秒倒计时；5 次失败后显示 10 分钟锁定。
- APP-02A：首次登录设置并确认 6 位支付密码，输入框禁复制/截屏，不在导航参数中传值。

##### 前端逻辑

1. 生成 PKCE `codeVerifier/codeChallenge`，Verifier 仅短时保存在加密内存/Keystore 会话域。
2. 验证码成功返回一次性授权码；调用 OAuth Token 端点交换 Token。
3. `payPasswordSet=false` 进入 APP-02A，否则进入 APP-03；开户和初始金等待后端事件完成，以钱包查询为准。
4. Access Token 失效时只允许一次串行刷新；刷新复用/失败清除安全存储并回登录。

##### 所需 API

| 方法 | 路径 | 用途 | 当前实现 |
|---|---|---|---|
| POST | `/api/v1/auth/consumer/code/send` | 发验证码 | 已实现 |
| POST | `/api/v1/auth/consumer/code/verify` | 验证并签发 PKCE 授权码 | 已实现 |
| POST | `/oauth2/token` | 交换/刷新 Token | 基线已实现 |
| PUT | `/api/v1/users/me/payment-password` | 首次设置支付密码 | 目标新增 |
| POST | `/api/v1/auth/consumer/payment-password-reset/challenges` | 修改密码前短信校验 | 目标新增 |

### 2.2.3 AI 主页与侧边栏（APP-03/04）

##### UI&交互

- 消息流支持用户文本、AI 文本、候选好友卡、补充金额卡、转账确认卡；输入最多 1,000 字。
- 顶栏消息入口和“扫一扫/收款/添加朋友”菜单；右划侧边栏可新建、搜索、重命名、删除会话。
- 删除会话二次确认；流中断显示“重新连接”，不得自动重复提交用户消息。

##### 前端逻辑

- POST 消息后消费 SSE：`message.delta` 追加文本，`friend.candidates` 展示候选，`transfer.prepared` 生成确认卡，`done` 收束。
- 只有用户点击确认卡才携带 `intentId` 跳 APP-09；AI 页面不展示、不收集支付密码。
- 会话列表游标分页；重命名 1～40 字；删除后精确移除 Query 缓存。

##### 所需 API

`GET/POST /api/v1/agent/conversations`、`PATCH/DELETE /api/v1/agent/conversations/{id}`、`GET/POST /api/v1/agent/conversations/{id}/messages`（目标新增；当前仅有单一 SSE 草案）。

### 2.2.4 消息、好友与群聊（APP-18～22、APP-32～34）

##### UI&交互

| 页面 | 关键展示/交互 |
|---|---|
| 消息列表 | 单聊/群聊混排，头像、名称、摘要、时间、未读数；每页≤50 |
| 单聊 | 文本、系统提示、转账卡；失去好友关系后输入区禁用但历史可看 |
| 通讯录/添加好友 | 昵称、MiniPay 号；完整手机号/MiniPay 号搜索；申请状态 |
| 创建群 | 至少选择 2 位好友，群名 2～20 字；上限 200 人 |
| 群聊 | 发送者头像/昵称、文本、脱敏转账卡；支持 @ 选择一名成员转账 |
| 群详情 | 群名、成员、添加/移除、退群、转让群主、解散；按身份控制按钮 |

##### 前端逻辑

- 发送消息前生成并持久到发送队列的 `clientMessageId`；网络重试复用该 ID。
- WebSocket 只做增量推送；重连后用服务端 `lastReadMessageId`/游标补拉，服务端数据为准。
- 群转账卡第三方永远不接收金额字段；第三方点击显示“你不在本次交易中”。交易双方再按 `transferId` 查询详情。
- 退群/被移除/解散后会话只读；历史不从本地缓存物理删除。

##### 操作按钮

| 操作 | 二次确认 | 显示/禁用控制 |
|---|---|---|
| 接受/拒绝申请 | 否 | 仅收件人且 PENDING |
| 删除好友 | 是 | 有效好友关系 |
| 群转账 | 付款页确认 | 双方是群成员且好友，群 ACTIVE |
| 移除成员/解散群 | 是 | 仅群主；不可移除自己，解散不可恢复 |
| 退出群 | 是 | 非最后一位群主；群主先转让或解散 |

##### 所需 API

`/friend-requests`、`/friends`、`/conversations`、`/messages`、`/read-cursor`、`/groups`、`/groups/{id}/members`、`/groups/{id}/owner`、`/groups/{id}/leave|dissolve`，以及 `/ws/v1/messages`。均为目标新增，后端当前无 `social-service`。

### 2.2.5 扫一扫与收款（APP-14 系列、APP-23）

##### UI&交互

- 首次扫描申请摄像头权限；拒绝后提供系统设置入口；识别中防重复触发。
- 仅接受 `FRIEND/PERSONAL_COLLECTION/BUSINESS_COLLECTION`，篡改、过期、停用统一错误页。
- 个人码可保存相册；经营开通填写 2～40 字经营名称并勾选协议；经营码页展示 AppID 与状态。

##### 前端逻辑

扫描内容不直接决定路由，统一提交 `/scan-resolutions` 由服务端返回 `nextAction`。个人码进入转账金额页；经营码进入支付确认；好友码进入资料/申请页。二维码 Token 不写埋点。

### 2.2.6 钱包、银行卡、充值、提现、转账、付款与账单（APP-09/11/12/13/15/30/31）

##### 表单字段

| 页面/字段 | 输入方式 | 必填 | 限制 | 数据类型/来源 |
|---|---|---:|---|---|
| 金额 | 数字键盘 | Y | >0，单笔≤1,000,000 分，最多 2 位小数 | 字符串输入→整数分 |
| 收款人 | 好友选择 | Y | 不得本人；唯一有效好友 | 好友 API |
| 备注 | 文本 | N | ≤50 字 | string |
| 支付方式 | 单选 | Y | `WALLET/ALIPAY/WECHAT`，确认后不可改 | intent 返回的 availableChannels |
| 支付密码 | 安全数字键盘 | Y | 6 位，不回显/不缓存 | 仅发 Identity |
| 充值银行卡 | 单选 | Y | ACTIVE 且属于本人 | 银行卡 API |
| 提现银行卡 | 单选 | Y | ACTIVE；默认卡优先 | 银行卡 API |
| 持卡人 | 文本 | Y | 2～40 字 | string |
| 卡号 | 数字 | Y | 16～19 位；提交后立即清空明文 | string |
| 开户行 | 可搜索单选 | Y | 服务端银行字典 | bankCode |
| 账单筛选 | 类型/状态/日期 | N | 默认 30 天，最大 365 天 | enum/date |

##### 前端逻辑

1. 付款按 `prepare → choose channel → payment authorization → confirm`；Identity 只收支付密码，Payment 只收一次性授权。
2. 支付方式确认后锁定。外部渠道返回页只触发查询，不能把同步返回当成功。
3. 银行充值创建订单后由银行回调入账；提现先冻结，结果不确定保持 `PROCESSING`。
4. 余额、订单、账单成功后按 wallet/order/bill 精确失效；禁止乐观修改权威余额。
5. 全部资金页固定显示：`MiniPay 沙箱资产，仅用于功能体验，不产生真实资金。`

##### 操作按钮

| 操作 | 二次确认 | 禁用条件 |
|---|---|---|
| 确认付款 | 支付密码即强确认 | intent 过期、渠道不可用、请求中 |
| 充值 | 是 | 未绑卡、余额上限将超过 ¥20,000、请求中 |
| 提现 | 是 | 未绑卡、余额不足、注销冷静期、渠道不可用 |
| 解绑银行卡 | 支付密码 | 处理中提现、仅剩默认出款卡时按后端规则 |

### 2.2.7 我的、订单、记忆、授权与注销（APP-17、APP-24～29）

- 我的：头像、昵称、MiniPay 号和功能入口；退出只清会话，不删除业务数据。
- 编辑资料：昵称 2～20 字（中文/字母/数字/下划线），头像先申请上传凭证再提交对象键。
- 订单：支付/转账/充值/提现混合分页；订单详情按类型渲染状态轨迹。
- 长期记忆：列表、单条删除、总开关；删除后立即从后续 AI 检索排除。
- 授权管理：本期固定空态，不请求不存在接口。
- 注销：先展示资格检查；提交进入 7 天冷静期。期间资金入口禁用并展示撤销入口。

### 2.2.8 商户 Web（MER-01～08）

##### UI&交互与前端逻辑

| 页面 | 筛选/展示 | 操作 |
|---|---|---|
| 登录 | 账号、密码、图形验证码 | BFF/OIDC 登录，失败不暴露账号存在性 |
| 看板 | 7/30 天；交易额/笔数、退款趋势、更新时间/时区 | 无数据空态 |
| 应用 | 名称、AppID、状态、创建时间 | 新建、编辑、启停；有交易不可删除 |
| 支付订单 | 时间、状态、支付方式 | 详情；满足条件可全额退款 |
| 退款/转账 | 时间、状态 | 只读详情/失败原因 |
| 钱包 | 可用/冻结、银行卡、近期账单 | 充值、提现、转账，写操作幂等+确认 |

商户 `merchantId` 不从 URL/表单传入，由 BFF Session 和后端租户上下文确定。页面隐藏不能替代后端鉴权。

### 2.2.9 运营 Web（OPS-02～08）

复用现有 `AuthGate`、`OpsShell`、`getSession/getCsrf/useSecureLogout` 和登录审计。PRD 编号与当前 `navigation.ts` 不一致，实施时以 PRD 为准：OPS-02 看板、03 商户、04 应用、05 支付、06 退款、07 转账、08 通知；额外账本/结算/系统/沙箱页面不进入本期验收导航。

| 页面 | 关键字段/交互 | 权限 |
|---|---|---|
| 看板 | 平台金额/笔数/成功率/退款/活跃商户/待办、范围与更新时间 | `ops.dashboard.read` |
| 商户 | 名称、状态、应用数、创建时间；增改启停 | `ops.merchant.read/write` |
| 应用 | 名称、AppID、所属商户、状态；增改启停 | `ops.application.read/write` |
| 支付/退款/转账 | 全局筛选、脱敏详情、状态轨迹 | 对应 `ops.*.read`；默认只读 |
| 通知 | 类型、状态、请求/响应摘要、重试历史 | `ops.notification.read/retry` |
| 登录审计 | 时间、管理员、方式、结果、requestId | 已实现 `ops.audit.read` |

## 2.3 API 契约与 JSON 响应

### 2.3.1 通用约定

- 成功直接返回资源，不包 `{code,data}`；分页 `{items,page,size,total}`。本文页码统一 **0 基**，当前代码亦为 0 基；OpenAPI 草案中的 1 基定义需统一修正。
- 时间为 UTC ISO-8601；金额为整数分；ID 为 UUIDv7 字符串。
- 错误 `Content-Type: application/problem+json`：

```json
{
  "type": "https://docs.minipay.local/problems/insufficient-balance",
  "title": "Insufficient balance",
  "status": 409,
  "code": "INSUFFICIENT_BALANCE",
  "requestId": "01JZREQ8Y3FBR9M2N5P6Q7R8S9",
  "detail": "可用余额不足"
}
```

### 2.3.2 登录

`POST /api/v1/auth/consumer/code/send`

```json
{
  "challengeId": "01982df0-f72a-7ab2-8f44-85cbace7c001",
  "maskedMobile": "138****1234",
  "expiresAt": "2026-07-31T10:10:00Z",
  "resendAfterSeconds": 60
}
```

`POST /api/v1/auth/consumer/code/verify`

```json
{
  "authorizationCode": "one-time-authorization-code",
  "expiresAt": "2026-07-31T10:06:00Z",
  "payPasswordSet": false
}
```

### 2.3.3 AI 会话与 SSE

`GET /api/v1/agent/conversations?page=0&size=20`

```json
{
  "items": [
    {"conversationId":"01982df0-f72a-7ab2-8f44-85cbace7c101","title":"转账给小明","status":"ACTIVE","lastActiveAt":"2026-07-31T10:08:01Z"}
  ],
  "page": 0,
  "size": 20,
  "total": 1
}
```

SSE 的 `data` 仍为 JSON：

```json
{
  "eventId": "01982df0-f72a-7ab2-8f44-85cbace7c102",
  "type": "transfer.prepared",
  "conversationId": "01982df0-f72a-7ab2-8f44-85cbace7c101",
  "payloadVersion": 1,
  "payload": {
    "intentId": "01982df0-f72a-7ab2-8f44-85cbace7c103",
    "receiver": {"userId":"01982df0-f72a-7ab2-8f44-85cbace7c104","nickname":"小明","maskedMobile":"138****1234"},
    "amountCent": 10000,
    "remark": "午餐"
  }
}
```

### 2.3.4 好友、会话与群聊

`GET /api/v1/friends?page=0&size=50`

```json
{
  "items": [{"userId":"01982df0-f72a-7ab2-8f44-85cbace7c201","nickname":"小明","minipayNo":"MP100023","avatarUrl":"https://cdn.example/avatar/1","relationStatus":"ACTIVE"}],
  "page": 0,
  "size": 50,
  "total": 1
}
```

`GET /api/v1/conversations?page=0&size=50`

```json
{
  "items": [
    {"conversationId":"01982df0-f72a-7ab2-8f44-85cbace7c202","type":"GROUP","name":"周末聚餐","avatarUrl":null,"lastMessage":{"type":"TEXT","summary":"晚上七点见","sentAt":"2026-07-31T10:09:00Z"},"unreadCount":2,"membershipStatus":"ACTIVE"}
  ],
  "page": 0,
  "size": 50,
  "total": 1
}
```

`POST /api/v1/groups` 返回 201：

```json
{
  "groupId": "01982df0-f72a-7ab2-8f44-85cbace7c203",
  "conversationId": "01982df0-f72a-7ab2-8f44-85cbace7c202",
  "name": "周末聚餐",
  "status": "ACTIVE",
  "ownerUserId": "01982df0-f72a-7ab2-8f44-85cbace7c200",
  "memberCount": 3,
  "maxMembers": 200,
  "createdAt": "2026-07-31T10:00:00Z"
}
```

`GET /api/v1/conversations/{id}/messages?before=&size=50`

```json
{
  "items": [
    {"messageId":"01982df0-f72a-7ab2-8f44-85cbace7c204","sender":{"userId":"01982df0-f72a-7ab2-8f44-85cbace7c201","nickname":"小明"},"type":"TRANSFER_CARD","text":null,"card":{"transferId":"01982df0-f72a-7ab2-8f44-85cbace7c205","displayText":"小明向小李发起一笔转账","viewerRole":"BYSTANDER","amountCent":null,"status":null},"sentAt":"2026-07-31T10:05:00Z"}
  ],
  "nextCursor": null,
  "hasMore": false
}
```

### 2.3.5 扫码解析

`POST /api/v1/scan-resolutions`

```json
{
  "resolutionId": "01982df0-f72a-7ab2-8f44-85cbace7c301",
  "codeType": "BUSINESS_COLLECTION",
  "nextAction": "CREATE_PAYMENT",
  "expiresAt": "2026-07-31T10:15:00Z",
  "payee": {"displayName":"MiniPay 演示咖啡店","minipayAppId":"mp_app_01982df0","avatarUrl":null},
  "allowedChannels": ["WALLET","ALIPAY","WECHAT"]
}
```

### 2.3.6 钱包、银行卡与账单

`GET /api/v1/wallets/me`

```json
{
  "walletId": "01982df0-f72a-7ab2-8f44-85cbace7c401",
  "availableAmountCent": 1000000,
  "frozenAmountCent": 0,
  "currency": "CNY",
  "status": "ACTIVE",
  "updatedAt": "2026-07-31T10:08:00Z"
}
```

`GET /api/v1/bank-cards`

```json
{
  "items": [{"bankCardId":"01982df0-f72a-7ab2-8f44-85cbace7c402","bankCode":"ICBC","bankName":"中国工商银行","maskedCardNo":"**** **** **** 1234","holderNameMasked":"张*","isDefault":true,"status":"ACTIVE","boundAt":"2026-07-31T08:00:00Z"}],
  "maxCards": 3
}
```

`GET /api/v1/wallets/me/bills?from=2026-07-01&to=2026-07-31&page=0&size=20`

```json
{
  "items": [{"billId":"01982df0-f72a-7ab2-8f44-85cbace7c403","businessType":"TRANSFER","businessNo":"TR202607310001","direction":"DEBIT","amountCent":10000,"balanceAfterCent":990000,"status":"SUCCEEDED","occurredAt":"2026-07-31T10:05:00Z"}],
  "page": 0,
  "size": 20,
  "total": 1
}
```

### 2.3.7 充值、提现与付款

`POST /api/v1/recharge-orders` 返回 201：

```json
{
  "rechargeOrderId":"01982df0-f72a-7ab2-8f44-85cbace7c501",
  "rechargeOrderNo":"RC202607310001",
  "amountCent":50000,
  "bankCard":{"bankCardId":"01982df0-f72a-7ab2-8f44-85cbace7c402","maskedCardNo":"****1234","bankName":"中国工商银行"},
  "status":"PROCESSING",
  "createdAt":"2026-07-31T10:10:00Z"
}
```

`POST /api/v1/withdrawal-orders` 返回 202：

```json
{
  "withdrawalOrderId":"01982df0-f72a-7ab2-8f44-85cbace7c502",
  "withdrawalOrderNo":"WD202607310001",
  "amountCent":20000,
  "status":"PROCESSING",
  "fundStatus":"RESERVED",
  "bankCard":{"maskedCardNo":"****1234","bankName":"中国工商银行"},
  "failureCode":null,
  "updatedAt":"2026-07-31T10:11:00Z"
}
```

`POST /api/v1/transfers` 返回 201（准备单）：

```json
{
  "intentId":"01982df0-f72a-7ab2-8f44-85cbace7c503",
  "intentNo":"TI202607310001",
  "receiver":{"userId":"01982df0-f72a-7ab2-8f44-85cbace7c201","nickname":"小明","maskedMobile":"138****1234"},
  "amountCent":10000,
  "remark":"午餐",
  "source":"GROUP_CHAT",
  "sourceConversationId":"01982df0-f72a-7ab2-8f44-85cbace7c202",
  "availableChannels":["WALLET","ALIPAY","WECHAT"],
  "status":"PENDING_CONFIRMATION",
  "expiresAt":"2026-07-31T10:20:00Z"
}
```

`POST /api/v1/transfers/{intentId}/confirm` 返回 200 或 202：

```json
{
  "transferId":"01982df0-f72a-7ab2-8f44-85cbace7c504",
  "transferNo":"TR202607310001",
  "intentId":"01982df0-f72a-7ab2-8f44-85cbace7c503",
  "amountCent":10000,
  "channel":"WALLET",
  "status":"SUCCEEDED",
  "failureCode":null,
  "updatedAt":"2026-07-31T10:12:00Z"
}
```

### 2.3.8 商户与运营

`GET /api/v1/merchant/dashboard?range=7d`

```json
{
  "timezone":"Asia/Shanghai",
  "from":"2026-07-25",
  "to":"2026-07-31",
  "dataAsOf":"2026-07-31T10:15:00Z",
  "todayPaymentAmountCent":120000,
  "totalPaymentAmountCent":9800000,
  "totalPaymentCount":328,
  "paymentTrend":[{"date":"2026-07-31","amountCent":120000,"count":8}],
  "refundTrend":[{"date":"2026-07-31","amountCent":10000,"count":1}]
}
```

`GET /api/v1/ops/merchants?page=0&size=20&status=ACTIVE`

```json
{
  "items":[{"merchantId":"01982df0-f72a-7ab2-8f44-85cbace7c601","merchantNo":"M20260731001","name":"MiniPay 演示咖啡店","status":"ACTIVE","applicationCount":1,"createdAt":"2026-07-31T08:00:00Z"}],
  "page":0,
  "size":20,
  "total":1
}
```

`GET /api/v1/ops/notifications/{id}`

```json
{
  "notificationId":"01982df0-f72a-7ab2-8f44-85cbace7c602",
  "type":"PAYMENT_CALLBACK",
  "status":"FAILED",
  "requestSummary":{"businessNo":"PO20260731001","eventType":"payment.succeeded"},
  "responseSummary":{"httpStatus":500,"bodyDigest":"sha256:..."},
  "attempts":[{"attempt":1,"occurredAt":"2026-07-31T10:00:00Z","result":"FAILED","nextRetryAt":"2026-07-31T10:01:00Z"}]
}
```

## 2.4 菜单与权限变动

| 端 | 菜单 | 页面 | 权限/规则 |
|---|---|---|---|
| Android | 无固定底栏；AI 首页顶栏和侧边栏入口 | APP-01～34 | 登录后按账户状态控制；注销冷静期禁资金入口 |
| 商户 | 工作台/应用中心/订单中心/钱包 | MER-02～08 | `merchant.*`，租户由 Session 派生 |
| 运营 | 工作台/商户/应用/支付/退款/转账/通知 | OPS-02～08 | `ops.*`；隐藏菜单不等于鉴权 |

现有运营导航中的通道、账本、结算、系统管理、沙箱模拟器属于扩展规划；除已实现登录审计外，不计入本期 PRD 验收。

## 2.5 模块划分与工作量评估

| 模块 | 细节 | 开发（人日） | 联调 | 自测 | 前端 | 后端依赖 |
|---|---|---:|---:|---:|---|---|
| Android 认证/安全 | PKCE、验证码、支付密码 | 3 | 1 | 1 | Android | Identity |
| AI | SSE、会话、确认卡 | 4 | 1 | 1 | Android | Agent/Payment |
| 好友/单聊/群聊 | WebSocket、离线、群管理、私密卡 | 8 | 2 | 2 | Android | Social/Payment |
| 扫码/收款 | 三码、经营开通、记录 | 4 | 1 | 1 | Android | Identity/Payment |
| 钱包/银行卡/资金单 | 充值提现、三通道、账单 | 7 | 2 | 2 | Android | Identity/Wallet/Payment |
| 我的 | 资料、订单、记忆、注销 | 4 | 1 | 1 | Android | 多服务 |
| 商户 Web | 登录壳、看板、应用、订单、钱包 | 7 | 2 | 2 | Web | Management BFF |
| 运营 Web | 复用壳，新增业务页 | 6 | 2 | 2 | Web | Management BFF |
| 共享契约/请求/组件 | OpenAPI 生成、Problem、表格状态 | 3 | 1 | 1 | Web/Android | 全部 |

# 3. 监控和埋点

| 事件 | 属性（仅允许） | 禁止属性 |
|---|---|---|
| `page_view` | pageId、appVersion、result | 手机号、Token、二维码 Token |
| `api_result` | endpointKey、status、code、durationBucket、requestId | 请求/响应正文、Cookie |
| `payment_submit/result` | source、channel、status、failureCode | 支付密码、银行卡、完整金额明细 |
| `message_send_result` | conversationType、messageType、result | 文本正文、成员身份 |
| `scan_result` | codeType、result | 原始二维码 |
| `auth_result` | method、result、failureCode | 手机号、验证码 |

前端监控需覆盖 JS/Kotlin 崩溃、白屏、API P95、SSE/WS 断线、轮询超时和敏感字段扫描。诊断通过 `requestId/traceId/businessNo` 关联。

# 4. 发布计划

1. 契约冻结：OpenAPI/AsyncAPI 评审，修正页码、充值模型和三通道枚举。
2. 后端顺序：Identity → Wallet → Payment → Social → Agent → Management BFF。
3. 前端顺序：共享契约 → Android 认证 → 钱包/支付 → 社交/群聊 → AI/扫码/我的 → 商户 → 运营。
4. 灰度：先测试环境单用户资金闭环，再多人社交/群聊，再商户/运营；所有页面固定沙箱标识。
5. 回滚：Web 保留上一静态版本；Android 使用版本开关关闭未稳定入口；后端不回滚已执行资金迁移，以向前修复/冲正为准。
6. 准入：CR、测试报告、监控核对、敏感日志扫描、越权测试、账本平衡和回滚演练全部通过。

# 5. 其他

## 5.1 风险评估

| 风险 | 等级 | 应对 |
|---|---|---|
| PRD 文件名与正文版本不一致 | 中 | 全链路标注 v1.7.0，建立 REQ/BR/AC 追踪 |
| 当前实现与目标差距大 | 高 | 按“现有/新增”拆任务，不把占位页计完成 |
| OpenAPI 草案与最新 PRD 冲突 | 高 | 先升兼容契约版本；禁止页面直接依赖旧 Recharge DTO |
| 外部通道同步返回被误判成功 | 高 | 只认订单查询/后端终态，处理中可恢复 |
| 群聊转账隐私泄露 | 高 | 第三方 DTO 不下发金额，UI 与接口双重校验 |
| Android 原有外卖占位污染范围 | 中 | 本期导航不暴露；Consumer H5 不发布业务入口 |

## 5.2 稳定性保障

- Android 最小宽度 320px；Web 重点验证 1280/1440/1920，Chrome/Edge 90+。
- 列表最大 100，消息每页最大 50；大列表虚拟化按性能测试决定。
- 断网重试只用于 GET 和带稳定幂等键的安全写操作；付款确认禁止 UI 连点。
- 所有错误页保留 requestId；401、403、409、422、429、502/503 分别映射登录、权限、冲突、校验、限流、依赖故障。

## 5.3 变更记录

| 日期 | 版本 | 标志 | 说明 |
|---|---|---|---|
| 2026-07-31 | 1.7.0 | A | 基于 PRD v1.7.0 与前后端当前实现生成；补齐群聊、银行卡和三支付通道；提供 JSON 契约示例 |

## 5.4 项目总结 / 复盘

上线后补充：实际工时、契约变更次数、联调缺陷、支付处理中恢复时长、群聊隐私专项结果及后续优化项。
