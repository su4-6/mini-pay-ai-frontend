# Android 构建与本地服务连接

调试包的服务地址可在本机 `local.properties` 中配置；该文件不应提交，也不要写入账号、令牌或其他凭据。

```properties
MINIPAY_DEBUG_IDENTITY_BASE_URL=http://10.0.2.2:8081
MINIPAY_DEBUG_PAYMENT_BASE_URL=http://10.0.2.2:8082
MINIPAY_DEBUG_WALLET_BASE_URL=http://10.0.2.2:8083
MINIPAY_DEBUG_COMMERCE_BASE_URL=http://10.0.2.2:8085
MINIPAY_DEBUG_AGENT_BASE_URL=http://10.0.2.2:8086
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

- Android 模拟器使用 `10.0.2.2` 访问开发机。
- USB 真机可执行 `adb reverse tcp:8081 tcp:8081`（以及 8082、8083、8085、8086），并保留默认的 `127.0.0.1` 地址。
- 局域网真机使用开发机可访问的局域网地址，所有地址必须指向同一套 MiniPay 服务环境。

开始资金或扫码联调前，确认 Identity、Payment、Wallet 都已健康运行。不同环境的 Payment 服务、签名密钥或数据库不能互相验证个人收款码。

## Release 构建（上线包）

Release 构建对**必填项做硬校验**：`MINIPAY_FOOD_H5_ORIGIN` 必须是 https origin，四个 `MINIPAY_RELEASE_*` 签名属性必须齐全；
任何一个缺失都会直接构建失败，避免产出“能装但连不上后端”的包。当前线上包的构建命令（工程目录 `android/`）：

```powershell
$env:JAVA_HOME    = "<JDK 21>"          # AGP 8.7 不支持更新的 JDK
$env:ANDROID_HOME = "<Android SDK>"
.\gradlew.bat :app:assembleRelease --no-daemon `
  -PMINIPAY_IDENTITY_BASE_URL=https://identity.su46proj.site `
  -PMINIPAY_PAYMENT_BASE_URL=https://payment.su46proj.site `
  -PMINIPAY_WALLET_BASE_URL=https://wallet.su46proj.site `
  -PMINIPAY_AGENT_BASE_URL=https://agent.su46proj.site `
  -PMINIPAY_COMMERCE_BASE_URL=https://commerce.su46proj.site `
  -PMINIPAY_FOOD_H5_ORIGIN=https://food.su46proj.site `
  -PMINIPAY_USER_AGREEMENT_URL=https://www.su46proj.site/legal/user-agreement `
  -PMINIPAY_PRIVACY_POLICY_URL=https://www.su46proj.site/legal/privacy `
  -PMINIPAY_AMAP_ANDROID_KEY=<高德 Android Key> `
  -PMINIPAY_RELEASE_STORE_FILE=<keystore 绝对路径> `
  -PMINIPAY_RELEASE_STORE_PASSWORD=<pwd> -PMINIPAY_RELEASE_KEY_ALIAS=<alias> -PMINIPAY_RELEASE_KEY_PASSWORD=<pwd>
```

打包后**务必解包自检**（只“能编译”不代表配置生效）：

| 检查 | 方法 | 期望 |
| --- | --- | --- |
| 高德 Key 真的进了清单 | `aapt2 dump xmltree --file AndroidManifest.xml app-release.apk`，看 `com.amap.api.v2.apikey` 的 value | 等于传入的 Key（**为空说明没传 `-P`，地图在 App 内必然不可用**） |
| 生产地址已烤入 | 解包后在各 dex 中搜索域名 | 6 个 `*.su46proj.site` 命中；`127.0.0.1` / `10.0.2.2` / `localhost` 全部不存在 |
| 版本与签名 | `aapt2 dump badging`、`apksigner verify --print-certs` | 版本号符合预期；签名 SHA-1 与高德后台绑定的包名/SHA1 一致 |

> 若地图白屏，请抓 `adb logcat | Select-String "amap|AMap|INVALID_USER|USERKEY"`：
> `INVALID_USER_KEY` 多为 Key 写错；`USERKEY_PLAT_NOMATCH` / `INVALID_USER_SCODE` 多为**签名 SHA1 或包名**不匹配。

## 高德 SDK 的隐私合规与定位排错

高德的三套 SDK（定位 / 地图 / 搜索）各自要求在**首次使用前**调用自己的
`updatePrivacyShow(...)` + `updatePrivacyAgree(...)`，任何一套漏掉都会让对应能力直接失败
（定位回调 `errorCode=12`，搜索抛「缺少隐私合规接口调用」）。声明统一放在
[`platform/AmapPrivacy.kt`](./app/src/main/java/com/minipay/mobile/platform/AmapPrivacy.kt)，
由 `MiniPayApplication.onCreate()` 在启动时执行一次（幂等，各处可安全重复调用）。

排错顺序：

1. 首页城市位置显示「定位失败（错误码 N）」时，先看这个码：
   `12` = 高德 Key/包名/签名绑定不匹配，或没有定位权限；`13` = 网络解析失败；
   `14` = GPS 未开启或信号弱；`18` = 系统定位服务被关闭。
2. 权限：`adb shell dumpsys package com.minipay.mobile | Select-String LOCATION` 看是否已授予。
3. Key 绑定：高德控制台里该 Key 必须绑定包名 `com.minipay.mobile` 与**发布签名**的 SHA-1
   （当前发布证书 SHA-1：`917B:49F1:794B:C667:5031:F7FE:5E9C:C6B5:8F8A:4B29`）。
4. 天气/POI 搜索依赖同一把 Key 的「Android 平台」授权；搜索失败不会让定位一起失败
   （代码里天气失败会降级为只显示城市）。

当前线上包：`versionName 0.1.7` / `versionCode 8`（本版换用新的高德 Android Key，
该 Key 绑定发布签名 SHA-1 `917B49F1…4B29` 与包名 `com.minipay.mobile`）。

- 下载（国内 CDN，快）：`https://dl.su46proj.site/downloads/minipay-latest.apk`
- 回退地址（R2 直连）：`https://download.su46proj.site/downloads/minipay-latest.apk`
- 发布后核对（两个入口都应一致）：40,252,600 B、MD5 `3706a61367b16fe2b67f31d843fc5900`、
  `aapt2 dump badging` 显示 `versionCode 8 / versionName 0.1.7`、清单里高德 Key 为新 Key。
- ⚠️ 换包后必须刷新腾讯云 CDN 缓存：`node _codex_digest/accept/tencent-cdn.mjs purge https://dl.su46proj.site/downloads/minipay-latest.apk`

> Key 值不进版本库：本地放在 `C:\minipay-keys\amap-android-key.txt`，打包脚本
> （工作区 `_codex_digest/accept/build-apk.ps1`）优先从这里读，其次读环境变量
> `MINIPAY_AMAP_ANDROID_KEY`。**只改环境变量容易在后台/管道调用里没被继承而打进旧 Key**（已踩过）。


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
