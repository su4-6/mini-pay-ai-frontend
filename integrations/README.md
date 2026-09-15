# integrations

本目录放**第三方 / 外部系统**的源码或产物，与 `apps/`（自研控制台）区分开。

| 目录 | 内容 | 状态 |
| --- | --- | --- |
| `yshop/admin-web` | YShop Drinks 后台（外卖后台）源码，用于构建 `suqihang/yshop-admin-web` | 外卖模块**当前已下线**（`food-admin.su46proj.site` 由维护页顶替），镜像仍在 Docker Hub |
| `yshop/food-h5` | YShop 点餐 H5（uni-app）源码，用于构建 `suqihang/yshop-food-h5` | 同上 |

关于外卖：

- 下线/恢复命令、维护页与路由切换方式见后端仓库 [deploy/k3s/README.md](https://github.com/su4-6/mini-pay-ai-backend/blob/main/deploy/k3s/README.md)。
- 这两个目录是那两个镜像的**唯一构建来源**，因此即使外卖暂时下线也保留在仓库里；
  若确定不再重建外卖镜像，可整目录删除（约 1900 个文件）。
- 构建：`integrations/yshop/*` 各自的 `pnpm install && pnpm build`，再由后端仓库 `deploy/k3s/images/yshop-*.Dockerfile` 打包。

> 早期那份「瘦身版」`integrations/yshop-lite/` 已随变更 #22 用正版 YShop 全量替换并删除，仅历史可查。
