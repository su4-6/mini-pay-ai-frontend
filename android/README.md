# Android 本地服务连接

调试包的服务地址可在本机 `local.properties` 中配置；该文件不应提交，也不要写入账号、令牌或其他凭据。

```properties
MINIPAY_DEBUG_IDENTITY_BASE_URL=http://10.0.2.2:8081
MINIPAY_DEBUG_PAYMENT_BASE_URL=http://10.0.2.2:8082
MINIPAY_DEBUG_WALLET_BASE_URL=http://10.0.2.2:8083
MINIPAY_DEBUG_COMMERCE_BASE_URL=http://10.0.2.2:8085
MINIPAY_DEBUG_AGENT_BASE_URL=http://10.0.2.2:8086
```

- Android 模拟器使用 `10.0.2.2` 访问开发机。
- USB 真机可执行 `adb reverse tcp:8081 tcp:8081`（以及 8082、8083、8085、8086），并保留默认的 `127.0.0.1` 地址。
- 局域网真机使用开发机可访问的局域网地址，所有地址必须指向同一套 MiniPay 服务环境。

开始资金或扫码联调前，确认 Identity、Payment、Wallet A 与 Wallet B 都已健康运行。不同环境的 Payment 服务、签名密钥或数据库不能互相验证个人收款码。

## 网络恢复边界

- OkHttp 的通用 `retryOnConnectionFailure` 保持关闭，避免自动重放 Token 轮换、支付、转账或其他写请求。
- 普通 GET/HEAD 遇到可恢复的瞬时连接异常时最多自动重试一次；证书、协议、SSE 和 WebSocket 连接不使用该策略。
- 当前可见页面会在网络恢复或应用再次回到前台时重新读取数据；后台页面不会被批量唤醒。
- 写操作失败后仍由对应业务流程使用原幂等键重试，不得把读取重试策略扩展到 POST/PUT/PATCH/DELETE。

## 系统语音能力

- AI 会话语音输入和回复朗读使用 Android 系统 `SpeechRecognizer` / `TextToSpeech`，MiniPay 不上传或持久化录音。
- 点击 AI 输入栏左侧按钮切换到语音模式，按住开始、松开识别、上滑取消；识别结果只回填到输入框，用户确认后再发送。
- AI 顶部声音开关只控制新完成回复的朗读；设备缺少中文系统语音引擎时会给出安全提示，不影响文本会话。
- 个人收款到账提醒默认开启，可在个人收款码页关闭。后台播报复用登录后的实时前台服务和 Wallet 到账 SSE，账单查询仍是到账事实来源。
- 强制停止应用、设备离线或厂商终止前台服务期间不保证播报，恢复后不会补读历史到账。
