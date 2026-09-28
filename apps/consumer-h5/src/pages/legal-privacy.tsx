import { AppShell } from '../components/AppShell';
import { ROUTES } from '../constants/routes';
import styles from './legal.module.less';
export default function LegalPrivacyPage(){return <AppShell title="隐私说明" backTo={ROUTES.login}><article className={styles.article}><h1>MiniPay 隐私说明</h1><p>本页说明演示系统如何处理浏览器中的信息，不构成正式商业产品的隐私政策。</p><h2>浏览器凭据</h2><p>浏览器只保存 HttpOnly 会话 Cookie。访问令牌、刷新令牌、设备标识和一次性支付授权令牌不会交给页面脚本。</p><h2>敏感信息</h2><p>支付密码只用于向身份服务申请一次性授权，不写入本地存储。实名认证照片随请求发送，不在 H5 中持久化。</p><h2>日志保护</h2><p>系统不得记录密码、验证码、完整手机号、证件号码、Cookie、Authorization 请求头或完整 Token。</p><h2>演示建议</h2><p>请始终使用测试手机号、测试身份和沙箱银行卡，不要提交真实隐私数据。</p></article></AppShell>}
