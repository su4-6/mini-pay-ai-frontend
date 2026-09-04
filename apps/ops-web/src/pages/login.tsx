import { useEffect, useMemo, useState } from 'react';
import { useLocation } from '@umijs/max';
import { Alert, App, Button, Form, Input, Tabs } from 'antd';
import type { CaptchaChallengeResponse } from '@minipay/api-contracts';
import { PortalSwitcher } from '@minipay/ui-desktop';
import { createCaptcha, sendSmsChallenge, submitLogin } from '../services/auth';
import styles from './login.module.less';

type Mode = 'password' | 'sms';

interface LoginValues {
  phone: string;
  password?: string;
  captchaCode?: string;
  smsCode?: string;
}

const PHONE_PATTERN = /^1[3-9]\d{9}$/;
const portalUrls = {
  merchant: typeof MERCHANT_WEB_PUBLIC_URL === 'string' ? MERCHANT_WEB_PUBLIC_URL : '/merchant/',
  ops: typeof OPS_WEB_PUBLIC_URL === 'string' ? OPS_WEB_PUBLIC_URL : '/ops/',
  admin: typeof ADMIN_WEB_PUBLIC_URL === 'string' ? ADMIN_WEB_PUBLIC_URL : '/'
};
const publicPath = typeof MINIPAY_PUBLIC_PATH === 'string' ? MINIPAY_PUBLIC_PATH : '/ops/';

/**
 * 运营平台登录页。
 *
 * 该页由 ops-web 独立托管。身份服务只提供验证码、登录和 OAuth2 会话接口；
 * 登录成功后整页跳转到身份服务保存的授权请求地址。
 */
export default function OpsLoginPage() {
  const { message } = App.useApp();
  const location = useLocation();
  const params = useMemo(() => new URLSearchParams(location.search), [location.search]);
  const initialMode: Mode = params.get('mode') === 'sms' ? 'sms' : 'password';
  const initialError = params.get('error') != null;

  const [form] = Form.useForm<LoginValues>();
  const [mode, setMode] = useState<Mode>(initialMode);
  const [captcha, setCaptcha] = useState<CaptchaChallengeResponse>();
  const [challengeId, setChallengeId] = useState<string>();
  const [sendingCode, setSendingCode] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [error, setError] = useState<string | null>(
    initialError ? '登录失败，请检查手机号、密码或验证码' : null
  );

  const refreshCaptcha = async () => {
    try {
      setCaptcha(await createCaptcha());
      form.setFieldValue('captchaCode', '');
    } catch (cause) {
      setError((cause instanceof Error && cause.message) || '验证码加载失败，请稍后重试');
    }
  };

  useEffect(() => {
    void refreshCaptcha();
    // 仅挂载时加载一次验证码；form 引用每次渲染都会变化，仅依赖空数组。
    // 注意：react-hooks 插件未在 eslint 配置中注册，此注释仅为文档，不起 lint 作用。
  }, []);

  useEffect(() => {
    if (countdown < 1) {
      return;
    }
    const timer = window.setInterval(() => setCountdown((value) => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [countdown]);

  const switchMode = (next: string) => {
    setMode(next as Mode);
    setChallengeId(undefined);
    setError(null);
    void refreshCaptcha();
  };

  const sendCode = async () => {
    try {
      await form.validateFields(['phone', 'captchaCode']);
      if (!captcha) {
        return;
      }
      setSendingCode(true);
      const result = await sendSmsChallenge(
        form.getFieldValue('phone'),
        captcha.captchaId,
        form.getFieldValue('captchaCode')
      );
      setChallengeId(result.challengeId);
      setCountdown(result.resendAfterSeconds);
      setError(null);
      void message.success(
        result.maskedPhone ? `验证码已发送至 ${result.maskedPhone}` : '验证码已发送'
      );
    } catch (cause) {
      if (cause instanceof Error) {
        setError(cause.message || '验证码发送失败，请重试');
      }
      await refreshCaptcha();
    } finally {
      setSendingCode(false);
    }
  };

  const onFinish = async (values: LoginValues) => {
    if (!captcha) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const redirectUrl =
        mode === 'password'
          ? await submitLogin('/login/password', {
              phone: values.phone,
              password: values.password ?? '',
              captchaId: captcha.captchaId,
              captchaCode: values.captchaCode ?? ''
            })
          : await submitLogin('/login/sms', {
              phone: values.phone,
              challengeId: challengeId ?? '',
              smsCode: values.smsCode ?? ''
            });
      window.location.assign(redirectUrl.redirectUrl);
    } catch (cause) {
      if (mode === 'sms') {
        setChallengeId(undefined);
        setCountdown(0);
      }
      await refreshCaptcha().catch(() => undefined);
      setError((cause instanceof Error && cause.message) || '登录失败，请检查手机号、密码或验证码');
    } finally {
      setSubmitting(false);
    }
  };

  const tabItems = [
    { key: 'password', label: '密码登录' },
    { key: 'sms', label: '验证码登录' }
  ];

  return (
    <div className={styles.loginPage}>
      <section className={styles.loginBrandPanel}>
        <div className={styles.loginLogo}>
          <img src={`${publicPath}minipay-logo.jpg`} alt="" />
          <div>
            <strong>MiniPay AI</strong>
            <span>智能支付开放平台</span>
          </div>
        </div>
        <div className={styles.loginPitch}>
          <h1>面向运营的一站式商户管理平台</h1>
          <p>统一审核商户入驻与应用接入，实时监控交易、退款与通知投递，全部敏感操作留痕可审计。</p>
        </div>
        <div className={styles.loginFacts}>
          <div>
            <strong>2 类</strong>
            <span>入驻·应用双审核</span>
          </div>
          <div>
            <strong>HMAC</strong>
            <span>回调验签保护</span>
          </div>
          <div>
            <strong>全程</strong>
            <span>操作留痕审计</span>
          </div>
        </div>
      </section>

      <section className={styles.loginFormPanel}>
        <div className={styles.loginBox}>
          <h2>登录运营平台</h2>
          <p>运营端与商户端登录相互独立，请使用平台管理员账号。</p>

          {error && <Alert className={styles.loginAlert} type="error" showIcon title={error} />}

          <Tabs
            className={styles.loginTabs}
            activeKey={mode}
            items={tabItems}
            onChange={switchMode}
          />

          <Form form={form} layout="vertical" requiredMark onFinish={onFinish}>
            <Form.Item
              name="phone"
              label="手机号"
              rules={[
                { required: true, message: '请输入手机号' },
                { pattern: PHONE_PATTERN, message: '请输入正确的手机号' }
              ]}
            >
              <Input placeholder="请输入管理员手机号" autoComplete="username" inputMode="numeric" maxLength={11} />
            </Form.Item>

            {mode === 'password' && (
              <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入登录密码' }]}>
                <Input.Password placeholder="请输入登录密码" autoComplete="current-password" />
              </Form.Item>
            )}

            {!challengeId && (
              <Form.Item label="图形验证码" required>
                <div className={styles.captchaRow}>
                  <Form.Item
                    name="captchaCode"
                    noStyle
                    rules={[{ required: true, message: '请输入图形验证码' }]}
                  >
                    <Input placeholder="请输入验证码" autoComplete="off" maxLength={6} />
                  </Form.Item>
                  <button
                    className={styles.captchaImage}
                    type="button"
                    aria-label="刷新图形验证码"
                    onClick={() => void refreshCaptcha()}
                  >
                    {captcha && (
                      <img
                        src={`/identity${captcha.imageUrl}?v=${encodeURIComponent(captcha.expiresAt)}`}
                        alt="图形验证码，点击刷新"
                      />
                    )}
                  </button>
                </div>
              </Form.Item>
            )}

            {mode === 'sms' &&
              (challengeId ? (
                <Form.Item label="短信验证码" required>
                  <div className={styles.smsRow}>
                    <Form.Item
                      name="smsCode"
                      noStyle
                      rules={[{ required: true, message: '请输入短信验证码' }]}
                    >
                      <Input placeholder="请输入 6 位验证码" autoComplete="one-time-code" maxLength={6} />
                    </Form.Item>
                    <Button
                      className={styles.sendCode}
                      htmlType="button"
                      loading={sendingCode}
                      disabled={countdown > 0 || submitting}
                      onClick={() => void sendCode()}
                    >
                      {countdown > 0 ? `${countdown} 秒后重发` : '重新获取'}
                    </Button>
                  </div>
                </Form.Item>
              ) : (
                <Button
                  className={styles.sendCode}
                  block
                  type="default"
                  htmlType="button"
                  loading={sendingCode}
                  disabled={submitting}
                  onClick={() => void sendCode()}
                >
                  获取短信验证码
                </Button>
              ))}

            <Button
              className={styles.loginSubmit}
              type="primary"
              htmlType="submit"
              loading={submitting}
              disabled={sendingCode || (mode === 'sms' && !challengeId)}
            >
              登录
            </Button>
          </Form>

          <PortalSwitcher current="ops" urls={portalUrls} />
          <div className={styles.loginFooter}>MiniPay AI 安全认证中心 · 登录操作将被审计</div>
        </div>
      </section>
    </div>
  );
}
