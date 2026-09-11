# 外卖 H5 产物来源与重建说明（yshop-lite → 正版 yshop food-h5）

## 为什么需要这份文档

`integrations/yshop/food-h5/unpackage/dist/build/h5-minipay/` 是**已提交的预构建产物**，
CI 直接把它打进镜像。它**无法用 npm 工具链重建**：

- 该工程的 `package.json` 里**没有任何 `@dcloudio/*` 依赖**，只有业务依赖
  （`@vant/area-data`、`flyio`、`pinia`、`sass`、`less`、`weixin-js-sdk` 等）
- `manifest.json` 为 `vueVersion: "3"` + 大量 `uni_modules`（uv-ui 全家桶）
- `scripts/build-minipay-h5.ps1` 依赖 **HBuilderX** 自带的编译器：
  `plugins\uniapp-cli-vite\node_modules\.bin\uni.cmd`

即：**这个 H5 的构建链是 HBuilderX 独有的，不是标准 npm/vite 工程。**

因此本项目对它的策略是：

1. **产物入库 + 哈希锁定**（本文件），CI 校验哈希，任何改动都会被检出
2. **重建步骤在此明确记录**，需要改 H5 时按下面流程操作并更新哈希

---

## 产物指纹（用于漂移检测）

| 项 | 值 |
|---|---|
| 文件数 | 149 |
| 清单总哈希 | `64eeda65060ccd89b6e9a1e6d22557bf301f3cdacb0c65b773454c9767ae7ba4`
| `index.html` | `f9a0f6fd8081f4d616e0b9a00f32ea14…` |
| `assets/index-DvqQQld_.js` | `3e7c4557ca650caa877559849d0ab707…` |

> 「清单总哈希」= 对 `sha256  相对路径` 逐行排序拼接后再做一次 SHA-256。
> 生成/校验脚本：`scripts/k3s/verify-h5-bundle.ps1`

### 已应用到产物的必要修正

产物里的 API 基址原本是**绝对地址且缺端口**：

```js
lw.config.baseURL = "http://food.minipay.localhost/app-api"   // 缺 :18080
```

H5 自己的 nginx 已经反代 `/app-api/` → `yshop-server:48080`，因此必须是相对路径。
**产物与源码 `.env.minipay` 均已改为 `/app-api`**；CI 校验脚本会断言产物里
不再出现 `http://food.minipay.localhost`。

---

## 需要用 HBuilderX 重建时的步骤

1. 安装 HBuilderX（3.99+，与 `manifest.json` 的 `uni_modules` 版本兼容）
2. 在仓库根执行：

```powershell
$env:HBUILDERX_ROOT = 'D:\HBuilderX.3.99\HBuilderX'   # 按实际路径
$env:MINIPAY_H5_API_URL = '/app-api'
powershell -ExecutionPolicy Bypass -File integrations/yshop/food-h5/scripts/build-minipay-h5.ps1 `
    -H5RouterBase '/' -ApiBaseUrl '/app-api'
```

3. 重新生成哈希并更新本文件：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/k3s/verify-h5-bundle.ps1 -Update
```

4. 重建镜像：

```powershell
docker build -f deploy/k3s/images/yshop-food-h5.Dockerfile `
  -t suqihang/yshop-food-h5:<version> integrations/yshop/food-h5
```

---

## 无 HBuilderX 的源码重建路径（**已打通并验证**）

`scripts/build-minipay-h5-cli.ps1` 可以**只依赖 npm 包**重建同一产物，
不要求安装 HBuilderX。已实测通过（2026-09-11，4.5 分钟，产出 149 个文件）。

```powershell
# 默认非破坏性：输出到 h5-minipay-cli，不覆盖在用产物
powershell -ExecutionPolicy Bypass -File integrations/yshop/food-h5/scripts/build-minipay-h5-cli.ps1

# 构建成功后，确认差异再决定是否替换在用产物
powershell -ExecutionPolicy Bypass -File scripts/k3s/verify-h5-bundle.ps1 -Update
```

### ⚠️ 四个必须遵守的约束（每条都是实测踩出来的，别"顺手简化"）

**1. 不要用 `latest` tag 装 `@dcloudio/*`。** 实测 `registry.npmmirror.com`：

| 包 | `latest` 实际指向 | 问题 |
|---|---|---|
| `@dcloudio/vite-plugin-uni` | `3.0.0-alpha-3000020210521001` | **2021 年的 alpha** |
| `@dcloudio/uni-h5` | `2.0.2-5020420260813001` | **Vue2 线** |
| `@dcloudio/uni-cli-shared` | `2.0.2-5020420260813001` | Vue2 线 |

正确的是**稳定版线**（非 alpha/beta/rc），六个包在该版本上**完全对齐**：

```
3.0.0-5020420260813003
```

**2. Vue / Vite 必须一起锁。** `@dcloudio/vite-plugin-uni` 的 peerDependency 是
`vite: 5.2.8`，`@dcloudio/uni-h5` 依赖 `@vue/server-renderer: 3.4.21`
→ 必须 `vue@3.4.21` + `vite@5.2.8`，否则版本错配会在**页面挂载时崩溃**而非构建失败。

**3. `pinia` 必须降到 `2.1.7`。** `pinia: ^2.1.6` 会解析到 2.3.1，其 peer 是
`vue ^3.5.11`，与 Vue 3.4.21 直接冲突（`npm error ERESOLVE`）。
`2.1.7` 的 peer 是 `vue ^3.3.0`，兼容。

**4. `UNI_INPUT_DIR` 必须指向工程根。** 本工程是 **HBuilderX 布局**（源码在根目录），
而 uni-app **CLI 工程**要求源码在 `src/` 下。不设该变量会直接报：

```
Error: ENOENT: no such file or directory, open '...\src\manifest.json'
```

> 另：构建必须从**纯 ASCII 路径**执行 —— 本仓库检出路径含中文、主机名非 ASCII，
> 会让工具链传给子进程的路径被代码页 936 转码。脚本会自动把工程复制到 `%TEMP%` 下构建。

### 与在用产物的等价性证据

用上述配方构建的产物与 HBuilderX 入库产物对比：

| 项 | 结果 |
|---|---|
| 文件总数 | **149 = 149** |
| 顶层结构 | 均为 `assets` / `static` / `index.html` |
| 同名路径 | 91 个 |
| 差异 | 58 个（**仅内容哈希命名不同**，工具链版本差异导致） |
| `handoff`（免登码）字符串 | 两边都有 ✅ |
| `locationContext`（位置凭证）字符串 | 两边都有 ✅ |
| `/app-api` 相对基址 | 两边都有 ✅ |
| `minipay.local` 绝对地址 | 两边都**没有** ✅ |
| 构建期红线校验 | 脚本内置（必备 asset + 禁绝对地址） |

> **仍未做的一步**：产物**不是字节级相同**（58 个哈希名不同）。
> 正式切换到 CLI 产物前，应在真机上重验
> 「免登码 → 下单 → 支付」整条 Web Message Bridge v3 链路。
> 在那之前，`-Publish` 默认关闭，在用产物保持哈希锁定。

