import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from '@umijs/max';
import { useQueryClient } from '@tanstack/react-query';
import { App, Button, Form, Input, Tabs } from 'antd';
import { PortalSwitcher } from '@minipay/ui-desktop';
import { merchantApi, type CaptchaChallenge } from '../services/merchant';
import styles from './index.module.less';

type Mode = 'password' | 'sms';
interface LoginValues { mobile: string; password?: string; captchaCode?: string; smsCode?: string; newPassword?: string }
const portalUrls = {
  merchant: typeof MERCHANT_WEB_PUBLIC_URL === 'string' ? MERCHANT_WEB_PUBLIC_URL : '/merchant/',
  ops: typeof OPS_WEB_PUBLIC_URL === 'string' ? OPS_WEB_PUBLIC_URL : '/ops/',
  admin: typeof ADMIN_WEB_PUBLIC_URL === 'string' ? ADMIN_WEB_PUBLIC_URL : '/'
};

const deviceId = () => {
  const existing = localStorage.getItem('minipay-merchant-device-id');
  if (existing) return existing;
  const value = crypto.randomUUID();
  localStorage.setItem('minipay-merchant-device-id', value);
  return value;
};

export default function MerchantLoginPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<LoginValues>();
  const [mode, setMode] = useState<Mode>('password');
  const [forgot, setForgot] = useState(false);
  const [captcha, setCaptcha] = useState<CaptchaChallenge>();
  const [challengeId, setChallengeId] = useState<string>();
  const [sendingCode, setSendingCode] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [countdown, setCountdown] = useState(0);

  const refreshCaptcha = async () => {
    try { setCaptcha(await merchantApi.createCaptcha()); form.setFieldValue('captchaCode', ''); }
    catch (error) { void message.error((error as Error).message); }
  };
  useEffect(() => { void refreshCaptcha(); }, []);
  useEffect(() => {
    if (countdown < 1) return;
    const timer = window.setInterval(() => setCountdown(value => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [countdown]);

  const tabItems = useMemo(() => [
    { key: 'password', label: '密码登录' }, { key: 'sms', label: '验证码登录' }
  ], []);

  const sendCode = async () => {
    try {
      await form.validateFields(['mobile', 'captchaCode']);
      if (!captcha) return;
      setSendingCode(true);
      const result = await merchantApi.sendLoginCode(
        form.getFieldValue('mobile'), captcha.captchaId, form.getFieldValue('captchaCode'));
      setChallengeId(result.challengeId);
      setCountdown(60);
      void message.success('验证码已发送');
    } catch (error) {
      if (error instanceof Error) void message.error(error.message);
      void refreshCaptcha();
    } finally { setSendingCode(false); }
  };

  const submit = async (values: LoginValues) => {
    if (!captcha) return;
    setSubmitting(true);
    try {
      if (mode === 'password' && !forgot) {
        await merchantApi.verifyLoginPassword({
          mobile: values.mobile, password: values.password!, captchaId: captcha.captchaId,
          captchaCode: values.captchaCode!, deviceId: deviceId()
        });
      } else {
        if (!challengeId) throw new Error('请先获取短信验证码');
        await merchantApi.verifyLoginCode({
          challengeId, code: values.smsCode!, deviceId: deviceId(),
          ...(forgot ? { resetPassword: values.newPassword } : {})
        });
      }
      void message.success(forgot ? '密码重置成功，已为你登录' : '登录成功');
      // 入口页把“未认证”的 session 结果缓存进 React Query（staleTime 30s），
      // 若不清理，门户页会复用旧缓存再次跳回登录页，造成“要登录两次”的假象。
      queryClient.removeQueries({ queryKey: ['merchant-session'] });
      queryClient.removeQueries({ queryKey: ['merchants'] });
      navigate('/dashboard', { replace: true });
    } catch (error) {
      void message.error((error as Error).message);
      if (!challengeId) void refreshCaptcha();
    } finally { setSubmitting(false); }
  };

  const switchMode = (next: string) => {
    setMode(next as Mode); setForgot(false); setChallengeId(undefined); void refreshCaptcha();
  };

  return <div className={styles.loginPage}>
    <section className={styles.loginBrandPanel}>
      <div className={styles.loginLogo}><img src={`${MINIPAY_PUBLIC_PATH}minipay-logo.jpg`} alt="" /><div><strong>MiniPay AI</strong><span>智能支付开放平台</span></div></div>
      <div className={styles.loginPitch}><h1>面向商家的一站式支付与经营平台</h1><p>一个商家账号可经营多个商户，通过应用 AppId、密钥和异步通知快速接入支付能力，统一查看交易、退款与资金。</p></div>
      <div className={styles.loginFacts}><div><strong>3 种</strong><span>沙箱支付渠道</span></div><div><strong>HMAC</strong><span>回调验签保护</span></div><div><strong>10 次</strong><span>自动通知重试</span></div></div>
    </section>
    <section className={styles.loginFormPanel}><div className={styles.loginBox}>
      <h2>{forgot ? '重置商户登录密码' : '登录商户平台'}</h2><p>商户端与运营端登录相互独立，登录后按账号展示名下商户。</p>
      {!forgot && <Tabs className={styles.loginTabs} activeKey={mode} items={tabItems} onChange={switchMode} />}
      <Form form={form} layout="vertical" requiredMark onFinish={submit}>
        <Form.Item name="mobile" label="手机号" rules={[{ required: true, message: '请输入手机号' }, { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的 11 位手机号' }]}><Input placeholder="请输入商家账号手机号" autoComplete="username" inputMode="numeric" maxLength={11} /></Form.Item>
        {mode === 'password' && !forgot && <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入商户登录密码' }]}><Input.Password placeholder="请输入商户登录密码" autoComplete="current-password" /></Form.Item>}
        {!challengeId && <Form.Item label="图形验证码" required>
          <div className={styles.captchaRow}><Form.Item name="captchaCode" noStyle rules={[{ required: true, message: '请输入图形验证码' }]}><Input placeholder="请输入验证码" maxLength={6} /></Form.Item><button className={styles.captchaImage} type="button" onClick={() => void refreshCaptcha()}>{captcha && <img src={`${captcha.imageUrl}?t=${encodeURIComponent(captcha.expiresAt)}`} alt="点击刷新验证码" />}</button></div>
        </Form.Item>}
        {(mode === 'sms' || forgot) && <>
          {!challengeId && <Button className={styles.sendCode} block type="default" htmlType="button" loading={sendingCode} onClick={() => void sendCode()}>获取短信验证码</Button>}
          {challengeId && <Form.Item label="短信验证码" required><div className={styles.smsRow}><Form.Item name="smsCode" noStyle rules={[{ required: true, message: '请输入短信验证码' }]}><Input placeholder="请输入 6 位短信验证码" maxLength={6} /></Form.Item><Button className={styles.sendCode} htmlType="button" disabled={countdown > 0} onClick={() => void sendCode()}>{countdown > 0 ? `${countdown} 秒后重发` : '重新发送'}</Button></div></Form.Item>}
          {forgot && challengeId && <Form.Item name="newPassword" label="新密码" rules={[{ required: true, message: '请输入新密码' }, { min: 12, message: '密码至少 12 位' }]}><Input.Password placeholder="至少 12 位，建议包含大小写、数字和符号" autoComplete="new-password" /></Form.Item>}
        </>}
        <Button className={styles.loginSubmit} type="primary" htmlType="submit" loading={submitting} disabled={sendingCode}>{forgot ? '重置密码并登录' : '登录'}</Button>
      </Form>
      <div className={styles.loginHelpers}><Button type="link" onClick={() => { setForgot(!forgot); setMode('sms'); setChallengeId(undefined); void refreshCaptcha(); }}>{forgot ? '返回密码登录' : '忘记密码？'}</Button></div>
      <PortalSwitcher current="merchant" urls={portalUrls} />
      <div className={styles.loginFooter}>MiniPay AI 安全认证中心 · 登录与敏感操作均会记录审计</div>
    </div></section>
  </div>;
}
