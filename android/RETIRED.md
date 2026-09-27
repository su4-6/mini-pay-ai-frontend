# Android 消费者端：已从线上下线（源码保留）

> 状态：**线上不再分发、不再维护**。源码、构建脚本与 OAuth 客户端注册**故意保留**，
> 以便随时本地跑起来或日后恢复。线上 C 端已改为消费者 H5：**https://app.su46proj.site**。

## 1. 为什么下线

| 原因 | 说明 |
|---|---|
| 缺陷积压无人维护 | App 上遗留问题较多，继续修的成本高于收益 |
| 分发成本 | 安装包 **40 MB**，每次发版都要走 R2 + 腾讯云 CDN，还要刷缓存、续 90 天 DV 证书 |
| 使用门槛 | 装包要允许"未知来源"，iOS 用户完全用不了，演示/答辩现场装包容易翻车 |
| 服务器资源 | 这台机器只有 **3723 MiB** 内存，App 依赖的语音通话（coturn）与外卖后端（commerce-service、yshop-mysql/redis）都在抢内存 |
| 收益 | H5 免安装、一条链接、跨端可用，且**同一套后端 API**，业务事实来源不变 |

## 2. 服务器侧删除了什么（以及怎么恢复）

| 项 | 位置 | 状态 | 恢复方式 |
|---|---|---|---|
| APK 对象 `downloads/minipay-latest.apk`（及其余 3 个历史包） | Cloudflare R2 桶 `minipay-downloads` | **已删除（2026-09-27）**，桶本身也已删除 | 建桶 → 重新上传对象 → 重新绑定自定义域名 |
| R2 自定义域名 `download.su46proj.site` | Cloudflare R2 | **已删除（2026-09-27）**，DNS 记录随之消失 | 重新添加自定义域名并把 DNS 指回 |
| DNS `download.su46proj.site` / `dl.su46proj.site` / `_dnsauth.dl` | Cloudflare DNS | **已删除（2026-09-27）** | 重新添加解析 |
| 腾讯云 CDN 域名 `dl.su46proj.site` 与其免费 DV 证书 | 腾讯云控制台 | **已删除（2026-09-27）**（CDN 域名 12 → 11） | 重新添加域名 + 申请证书 + 回源 R2 |
| 两个 Worker 上的 R2 绑定（`DOWNLOADS` / `MINIPAY_DOWNLOADS`） | Cloudflare Workers | **已清除（2026-09-27）**：Worker 重新上传后绑定为空、路由未变 | 在 `wrangler.toml` 恢复 `[[r2_buckets]]` 并重新部署 |
| 宿主 nginx 下载配置与 `/var/www/**/downloads`（约 1.03 GB APK） | 源站服务器 | **已删除（2026-09-27）**：`minipay-direct-download.conf`（含软链）、两处 `/downloads/` location、`/var/www/pay.su46proj.site/downloads`、`/var/www/html/downloads` | 反正是宿主 nginx 时代的死配置（systemd nginx 已 inactive），真要恢复按 `cloudflare-workers/R2-DEPLOYMENT.md` 重建 |
| 服务器上的 `apps/minipay-frontend/android` 源码副本 + `run-android.sh` + `deploy-login-map-android-*.sh` | 源站服务器 | **已删除（2026-09-27）**，源码以本仓库为准 | `git clone` 本仓库即可 |
| Cloudflare 针对 `/downloads/*` 的缓存规则 | Cloudflare 控制台 | 未处理：现有 token 缺 `Rulesets` 权限读不到（也无旧版页面规则）。**已无实际影响**（没有主机再服务该路径） | 加权限后在控制台确认/删除 |
| `commerce-service`（点餐外卖后端） | K3s Deployment | 已缩到 0 副本 | 删 `deploy/k3s/overlays/server/kustomization.yaml` 里对应 patch，`kubectl -n minipay scale deploy commerce-service --replicas=1` |
| 原米灵实现 `agent-service` | K3s Deployment | 已缩到 0 副本（由 `miling-service` 取代） | 同上，scale 回 1 |
| `yshop-mysql` / `yshop-redis` | 宿主机 Compose | 已从服务器面排除（`profiles: !override ["food-disabled"]`） | 删掉 `deploy/compose-infra/compose.server.yaml` 里的 profiles 覆盖；数据卷未删，数据保留 |
| `coturn`（TURN/STUN，语音通话） | 宿主机 Compose | 只在 `voice` profile 下启动，服务器面不启用 | 用 `--profile voice` 启动 |

> 说明：**删除 APK 不释放内存**。APK 是 R2 上的对象存储 + 服务器上的静态文件（省的是磁盘与下行流量，
> 本次服务器侧共释放约 1 GB：宿主 `/var/www` 里的 1.03 GB APK 与备份包）；
> 真正释放内存的是上表里 K3s 与宿主机的那几个进程。

### 2.1 下线后的线上校验（2026-09-27）

| 入口 | 结果 |
|---|---|
| `https://download.su46proj.site/downloads/minipay-latest.apk` | 404（首次 curl 仍命中边缘缓存旧 200，三级 purge 后 404） |
| `https://dl.su46proj.site/downloads/minipay-latest.apk` | 404（CDN 域名已删） |
| `http://122.152.221.201/minipay-latest.apk` | 404（宿主下载 vhost 已停用） |
| `https://pay.su46proj.site/`、`https://su46proj.site/`、`www` | 200，`apk_refs=0`，只剩指向 `app.su46proj.site` 的 H5 入口 |
| 源站服务器 `find / -iname '*.apk'` | 为空 |
| 线上验收 `scripts/k3s/acceptance-server.ps1`（H5 + 转账 + 商户扫码付款） | **21 passed / 0 failed** |

执行明细（含 Cloudflare 凭据位置与"服务器无 node 时如何上传 Worker"）见根目录
`cloudflare-workers/R2-DEPLOYMENT.md`。

## 3. 故意保留了什么

- **`minipay-android` OAuth2 客户端注册**（identity-service，`deploy/k3s/overlays/server` 的客户端 bootstrap）：
  它的存在**不占内存、不暴露任何入口**，但删掉之后保留下来的源码就再也登录不了。
  决策：保留注册，让源码保持可运行。若要彻底断开 App，删掉
  `services/identity-service/.../infrastructure/security/SecurityConfiguration.java`
  里 `registerAndroidClient(...)` 的调用并在下一次 identity 部署后清理 `oauth2_registered_client` 中对应行。
- **`android/` 源码、Gradle 工程、`scripts/run-android.sh`、`android/README.md`**：全部原样保留。
- **`minipay.token-audience = consumer-api`** 与 App 用到的 scope 定义：H5 复用同一套。

## 4. 现在要跑 App 需要什么

线上支撑已经拆掉了，所以**不能**拿旧 APK 直接跑通全部功能：

| 功能 | 现在能否用 | 原因 |
|---|---|---|
| 登录、钱包、账单、转账、收款码 | 需要本地起后端 | 线上 `consumer-bff` 已改造成 H5 专用会话（Cookie），App 走的是 Bearer + PKCE 直连 |
| 米灵 AI 对话 | ❌ | 线上 `agent-service` 已缩到 0 副本，AI 由 `miling-service` 提供 |
| 点餐外卖 | ❌ | `commerce-service`、`yshop-*`、`yshop-mysql/redis` 全部下线 |
| 语音通话 | ❌ | `coturn` 未启用 |

本地全量起后端后仍可构建运行，步骤见 `RUNBOOK.md` 的 Android 章节。

## 5. 已知问题

不再修复。保留此节是为了让后来者知道"这里为什么是这样"，而不是当待办清单：

- App 侧的缺陷没有系统登记，属于"用户反馈即修"的临时状态，这也是下线决策的直接原因。
- 高德 Android Key 绑定包名 `com.minipay.mobile` 与签名 SHA-1，换签名或换包名都要重新在
  高德后台配置，否则定位降级。
- Release 构建强依赖四个签名环境变量（`MINIPAY_RELEASE_STORE_FILE/PASSWORD`、
  `MINIPAY_RELEASE_KEY_ALIAS/KEY_PASSWORD`），缺失时构建直接失败而不是产出不可用包。

## 6. 相关文档

- 消费者 H5（现行 C 端）：`apps/consumer-h5/README.md`
- 服务器部署与内存收敛：`../deploy/k3s/README.md`（后端仓）
- 本地联调：`../RUNBOOK.md`（后端仓）
