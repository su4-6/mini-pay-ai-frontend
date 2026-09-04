# Android 账号管理接入说明

账号管理入口使用稳定路由 `profile/account-security`。Android 仅展示 Identity 返回的脱敏联系方式和支付密码状态，不以默认值替代服务端数据，也不持久化接口返回的完整联系方式。

## Identity 契约

正式接口如下：

- `GET /api/v1/users/me/account-security`
- `POST /api/v1/users/me/phone-change-challenges`
- `PUT /api/v1/users/me/phone`
- `POST /api/v1/users/me/email-verification-challenges`
- `PUT /api/v1/users/me/email`
- `DELETE /api/v1/users/me/email`
- `POST /api/v1/users/me/payment-password-change-challenges`
- `POST /api/v1/users/me/payment-password-change-challenges/{challengeId}/verifications`
- `POST /api/v1/users/me/payment-password-changes`

支付密码修改分为三步：Android 将安全存储中的当前手机号和设备 ID 临时提交以创建短信挑战；验证码通过后取得最长五分钟、绑定用户/设备/用途且单次有效的验证凭据；最后使用该凭据提交新的六位数字支付密码。支付密码未设置时，账号管理只展示真实状态，首次设置仍使用原有流程。

所有写请求发送 `X-Request-Id` 和 16～128 字符的 `Idempotency-Key`。只有结果不确定的写请求重试才复用原幂等键。GET 仅对网络错误或 5xx 安全重试一次。RFC 9457 响应只消费稳定的 `code`、`requestId` 和重试秒数，不展示后端 `detail`。

概览优先读取 `paymentPasswordSet`；发布过渡期内，如果旧版 Identity 未返回该字段，Android 会读取 `/api/v1/users/me/capabilities` 的 `payPasswordSet`，但不会伪造默认状态。

## 敏感数据边界

- 验证码、支付密码和支付密码验证凭据只存在于当前内存流程，不进入导航参数、`SavedStateHandle`、SharedPreferences、Room、日志或埋点。
- 支付密码页面使用应用内数字键盘，不开放复制或明文显示，并在页面生命周期内设置 `FLAG_SECURE`。
- 手机换绑成功后，Android 清除 Access/Refresh Token、用户 ID、手机号、认证声明及全部用户级 Room 数据，并销毁已认证导航树；设备 ID 保留。
- Identity 同时撤销该用户全部 Refresh Token 家族。其他设备会在 Access Token 到期或尝试刷新时退出。
- 普通退出复用相同的本地数据清理能力，并先尽力撤销服务端 Refresh Token。

## 验证

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon
```

连接测试需要可用的 Android SDK、模拟器或真机。自动化测试必须使用 Fake/Mock 获取验证码，不得把真实手机号、邮箱、验证码或支付密码写入测试日志和固定测试数据。
