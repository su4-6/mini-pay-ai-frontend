# docs 索引

前端仓库的文档分三类：**规范**（写代码前必读）、**设计资料**（产品/交互/视觉依据）、**专题**（单点机制说明）。
原型图统一放在 `prototypes/`。

| 文档 | 内容 | 什么时候看 |
| --- | --- | --- |
| [PROJECT_STANDARDS.md](./PROJECT_STANDARDS.md) | 工程规范：目录约定、命名、状态管理、请求封装、提交流程 | 写代码前 |
| [MiniPay-AI-PRD-v1.5.0.md](./MiniPay-AI-PRD-v1.5.0.md) | 产品需求：角色、场景、功能清单与验收口径 | 需求对齐 |
| [MiniPay-AI-前端系统分析与设计-v1.7.0.md](./MiniPay-AI-前端系统分析与设计-v1.7.0.md) | 三端架构、路由与权限、会话与 OAuth 流程、构建与部署设计 | 改架构 / 加页面 |
| [MiniPay-Android-个人中心需求文档-v1.0.md](./MiniPay-Android-个人中心需求文档-v1.0.md) | App 个人中心、钱包、订单、消息等页面需求 | 改 Android |
| [android-account-security.md](./android-account-security.md) | App 账号安全：短信登录、支付密码、设备与换绑 | 改 Android 账号链路 |
| [food-bridge-v1.md](./food-bridge-v1.md) | 外卖 H5 与 App 的桥接协议（`window.MiniPayBridge`）与白名单 | 改外卖入口 |
| [B端前端业务功能调试清单.md](./B端前端业务功能调试清单.md) | 运营端/商户端逐页功能与调试清单（人工验收用） | 联调、回归 |
| [performance.md](./performance.md) | 构建配置取舍、首屏体积实测、缓存策略与后续优化方向 | 做性能优化 |
| `prototypes/` | 运营端、商户端与 App 的交互原型图（22 张） | 对视觉 / 还原设计 |

> 运行、构建、演示账号与性能数据见仓库根 [README.md](../README.md)；
> Android 打包与签名见 [android/README.md](../android/README.md)；
> 外卖源码（构建来源）见 [integrations/README.md](../integrations/README.md)。
