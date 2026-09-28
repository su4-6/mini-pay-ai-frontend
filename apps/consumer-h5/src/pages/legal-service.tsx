import { AppShell } from '../components/AppShell';
import { ROUTES } from '../constants/routes';
import styles from './legal.module.less';
export default function LegalServicePage(){return <AppShell title="演示服务说明" backTo={ROUTES.login}><article className={styles.article}><h1>MiniPay 演示服务说明</h1><p>MiniPay 是用于学习、作品展示和系统联调的沙箱项目，不提供真实金融服务。</p><h2>演示范围</h2><p>短信、实名认证、银行卡、充值、提现、转账和付款均可能使用固定验证码、测试身份或沙箱通道，页面金额不代表真实资金。</p><h2>使用提示</h2><p>请勿输入真实银行卡、真实身份证照片、常用密码或其他敏感资料。请勿将演示结果作为真实支付凭证。</p><h2>异常处理</h2><p>资金操作结果未知时先到账单核对，不要重复提交。系统会展示 requestId 方便定位问题。</p></article></AppShell>}
