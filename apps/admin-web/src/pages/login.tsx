import { useEffect, useState } from 'react';
import { Alert, App, Button, Form, Input, Tabs } from 'antd';
import { PortalSwitcher } from '@minipay/ui-desktop';
import {
  createCaptcha,
  loginWithPassword,
  loginWithSms,
  sendSmsChallenge,
  type CaptchaChallenge
} from '../services/auth';
import styles from './login.module.less';

interface LoginValues {
  phone: string;
  password?: string;
  captchaCode?: string;
  smsCode?: string;
}
type Mode = 'password' | 'sms';
const portalUrls = {
  merchant: typeof MERCHANT_WEB_PUBLIC_URL === 'string' ? MERCHANT_WEB_PUBLIC_URL : '/merchant/',
  ops: typeof OPS_WEB_PUBLIC_URL === 'string' ? OPS_WEB_PUBLIC_URL : '/ops/',
  admin: typeof ADMIN_WEB_PUBLIC_URL === 'string' ? ADMIN_WEB_PUBLIC_URL : '/'
};

const PHONE_PATTERN = /^1[3-9]\d{9}$/;

export default function AdminLoginPage() {
  const { message } = App.useApp();
  const [form] = Form.useForm<LoginValues>();
  const [mode, setMode] = useState<Mode>('password');
  const [captcha, setCaptcha] = useState<CaptchaChallenge>();
  const [challengeId, setChallengeId] = useState<string>();
  const [sendingCode, setSendingCode] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [error, setError] = useState('');

  const refreshCaptcha = async () => {
    try {
      setCaptcha(await createCaptcha());
      form.setFieldValue('captchaCode', '');
    } catch (cause) {
      setError((cause as Error).message);
    }
  };

  useEffect(() => { void refreshCaptcha(); }, []);
  useEffect(() => {
    if (countdown < 1) return;
    const timer = window.setInterval(() => setCountdown(value => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [countdown]);

  const sendCode = async () => {
    try {
      await form.validateFields(['phone', 'captchaCode']);
      if (!captcha) return;
      setSendingCode(true);
      setError('');
      const result = await sendSmsChallenge(
        form.getFieldValue('phone'),
        captcha.captchaId,
        form.getFieldValue('captchaCode') ?? ''
      );
      setChallengeId(result.challengeId);
      setCountdown(result.resendAfterSeconds);
      void message.success(result.maskedPhone ? `验证码已发送至 ${result.maskedPhone}` : '验证码已发送');
    } catch (cause) {
      setError((cause as Error).message || '验证码发送失败，请重试');
      await refreshCaptcha().catch(() => undefined);
    } finally {
      setSendingCode(false);
    }
  };

  const switchMode = (next: string) => {
    setMode(next as Mode);
    setChallengeId(undefined);
    setCountdown(0);
    setError('');
    void refreshCaptcha();
  };

  const submit = async (values: LoginValues) => {
    if (mode === 'sms' && !challengeId) {
      setError('请先获取短信验证码');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      if (mode === 'password') {
        if (!captcha) return;
        await loginWithPassword({
          phone: values.phone,
          password: values.password ?? '',
          captchaId: captcha.captchaId,
          captchaCode: values.captchaCode ?? ''
        });
      } else {
        await loginWithSms({
          phone: values.phone,
          challengeId: challengeId ?? '',
          smsCode: values.smsCode ?? ''
        });
      }
      // Identity now owns the authenticated administrator session. Start the
      // Admin BFF authorization flow explicitly so its independent web session
      // is established without falling through the operations login page.
      window.location.assign('/oauth2/authorization/minipay-admin');
    } catch (cause) {
      setError((cause as Error).message || '登录失败，请检查手机号或短信验证码');
      if (mode === 'sms') {
        setChallengeId(undefined);
        setCountdown(0);
      }
      await refreshCaptcha().catch(() => undefined);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className={styles.page}>
      <section className={styles.brand}>
        <div className={styles.logo}>
          <img src={`${MINIPAY_PUBLIC_PATH}minipay-logo.svg`} alt="MiniPay" />
          <div><strong>MiniPay AI</strong><span>智能支付开放平台</span></div>
        </div>
        <div className={styles.pitch}>
          <span className={styles.eyebrow}>SYSTEM CONTROL CENTER</span>
          <h1>系统管理与安全控制中心</h1>
          <p>独立于运营端的系统管理员入口，用于账号安全、全局查询、审计与后台人员管理。</p>
        </div>
        <div className={styles.badges}><span>独立会话</span><span>最高权限</span><span>全程审计</span></div>
      </section>
      <section className={styles.panel}>
        <div className={styles.box}>
          <span className={styles.panelEyebrow}>安全认证</span>
          <h2>登录系统管理平台</h2>
          <p>仅限系统管理员使用，运营账号请从运营端登录。</p>
          {error ? <Alert type="error" showIcon title={error} className={styles.alert} /> : null}
          <Tabs
            activeKey={mode}
            onChange={switchMode}
            items={[
              { key: 'password', label: '密码登录' },
              { key: 'sms', label: '验证码登录' }
            ]}
          />
          <Form form={form} layout="vertical" onFinish={submit}>
            <Form.Item name="phone" label="管理员手机号" rules={[
              { required: true, message: '请输入管理员手机号' },
              { pattern: PHONE_PATTERN, message: '请输入正确的 11 位手机号' }
            ]}>
              <Input autoComplete="username" inputMode="numeric" maxLength={11} placeholder="请输入系统管理员手机号" />
            </Form.Item>
            {mode === 'password' ? (
              <Form.Item name="password" label="登录密码" rules={[{ required: true, message: '请输入登录密码' }]}>
                <Input.Password autoComplete="current-password" placeholder="请输入管理员登录密码" />
              </Form.Item>
            ) : null}
            {!challengeId ? (
              <>
                <Form.Item label="图形验证码" required>
                  <div className={styles.captcha}>
                    <Form.Item name="captchaCode" noStyle rules={[{ required: true, message: '请输入图形验证码' }]}>
                      <Input maxLength={6} autoComplete="off" placeholder="请输入验证码" />
                    </Form.Item>
                    <button type="button" onClick={() => void refreshCaptcha()} aria-label="刷新图形验证码">
                      {captcha ? <img src={`/identity${captcha.imageUrl}?v=${encodeURIComponent(captcha.expiresAt)}`} alt="图形验证码，点击刷新" /> : null}
                    </button>
                  </div>
                </Form.Item>
                <Button className={styles.sendCode} style={{ display: mode === 'sms' ? undefined : 'none' }} block htmlType="button" loading={sendingCode} disabled={submitting} onClick={() => void sendCode()}>
                  获取短信验证码
                </Button>
              </>
            ) : (
              <Form.Item label="短信验证码" required>
                <div className={styles.sms}>
                  <Form.Item name="smsCode" noStyle rules={[{ required: true, message: '请输入短信验证码' }]}>
                    <Input maxLength={6} autoComplete="one-time-code" placeholder="请输入 6 位短信验证码" />
                  </Form.Item>
                  <Button htmlType="button" loading={sendingCode} disabled={countdown > 0 || submitting} onClick={() => void sendCode()}>
                    {countdown > 0 ? `${countdown} 秒后重发` : '重新获取'}
                  </Button>
                </div>
              </Form.Item>
            )}
            <Button className={styles.submit} block type="primary" htmlType="submit" loading={submitting} disabled={(mode === 'sms' && !challengeId) || sendingCode}>
              登录管理平台
            </Button>
          </Form>
          <PortalSwitcher current="admin" urls={portalUrls} />
          <footer>MiniPay AI 系统安全中心 · 管理操作全部留痕</footer>
        </div>
      </section>
    </main>
  );
}
