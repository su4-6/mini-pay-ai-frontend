# MiniPay 外卖 H5 构建

使用 HBuilderX 选择 `minipay` 环境构建 H5，或在构建进程中设置：

```text
NODE_ENV=production
VUE_APP_MINIPAY_FOOD=true
VUE_APP_API_URL=https://api.food.minipay.local/app-api
VITE_MINIPAY_FOOD=true
VITE_API_URL=https://api.food.minipay.local/app-api
```

H5 Origin 固定为 `https://food.minipay.local`，必须与 Android、Commerce 和 yshop 的配置一致。
该构建通过 Web Message Bridge v3 获取一次性免登码和短时位置凭证；H5 不接收原始经纬度。
直接在普通浏览器打开时只显示 MiniPay 使用提示，不能发起付款。

本机已安装 HBuilderX 时，也可以离线构建，不要求登录 DCloud 账号：

```powershell
.\scripts\build-minipay-h5.ps1
```

真机通过 `adb reverse` 联调时使用固定的本地构建入口，避免把
`api.food.minipay.local` 打入产物后在手机上出现 `ERR_NAME_NOT_RESOLVED`：

```powershell
.\scripts\build-minipay-h5-local.ps1
adb reverse tcp:48081 tcp:48081
adb reverse tcp:4173 tcp:4173
```

本地构建只把 API 指向 `http://127.0.0.1:48081/app-api`；生产构建仍必须显式传入
独立 HTTPS API 域名。

默认产物目录为 `unpackage/dist/build/h5-minipay`。脚本会加载 `.env.minipay`，并同时兼容新版 Vite 的 `VITE_*` 与旧版构建变量。
