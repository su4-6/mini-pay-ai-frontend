import { Link, useLocation, useNavigate } from '@umijs/max';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Checkbox, Input } from 'antd-mobile';
import dayjs from 'dayjs';
import { useEffect, useState } from 'react';
import { InlineNotice } from '../components/InlineNotice';
import { ProblemNotice } from '../components/ProblemNotice';
import { ROUTES } from '../constants/routes';
import { useSessionQuery } from '../hooks/useSession';
import { useNow } from '../hooks/useNow';
import { queryKeys } from '../query/keys';
import { createSession, ensureCsrfToken, requestSmsChallenge } from '../services/session';
import type { SmsChallenge } from '../types/consumer';
import { isMobile, isSmsCode, normalizeDigits } from '../utils/validators';
import { readSearchParam, sanitizeRedirect } from '../utils/redirect';
import styles from './login.module.less';

const RESEND_COOLDOWN_SECONDS = 60;
/** 演示环境固定验证码（后端返回 demoCode 时以后端为准）。 */
const DEMO_CODE = '123456';

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const sessionQuery = useSessionQuery();
  const now = useNow(1_000);

  const redirect = sanitizeRedirect(readSearchParam(location.search ?? '', 'redirect'), ROUTES.home);

  const [mobile, setMobile] = useState('');
  const [code, setCode] = useState('');
  const [agreed, setAgreed] = useState(false);
  const [challenge, setChallenge] = useState<SmsChallenge | null>(null);
  const [resendAt, setResendAt] = useState<number | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  // 登录前也必须先取一次 CSRF（契约要求：登录前与登录后都要先 GET /api/v1/csrf）。
  useEffect(() => {
    void ensureCsrfToken().catch(() => undefined);
  }, []);

  // 已登录用户直接回跳，避免重复登录。
  useEffect(() => {
    if (sessionQuery.data?.authenticated) {
      navigate(sessionQuery.data.onboardingRequired ? ROUTES.onboarding : redirect, { replace: true });
    }
  }, [navigate, redirect, sessionQuery.data]);

  const sendMutation = useMutation({
    mutationFn: requestSmsChallenge,
    onSuccess: (result) => {
      setChallenge(result);
      setResendAt(Date.now() + RESEND_COOLDOWN_SECONDS * 1_000);
      setCode('');
      setFormError(null);
    }
  });

  const loginMutation = useMutation({
    mutationFn: createSession,
    onSuccess: (profile) => {
      setFormError(null);
      setCode('');
      // 登录成功后会话轮换：这里精确写入并失效会话查询（CSRF 已在 service 内清空）。
      queryClient.setQueryData(queryKeys.session, profile);
      void queryClient.invalidateQueries({ queryKey: queryKeys.session });
      navigate(profile.onboardingRequired ? ROUTES.onboarding : redirect, { replace: true });
    }
  });

  const cooldownSeconds = resendAt ? Math.max(0, Math.ceil((resendAt - now) / 1_000)) : 0;
  const challengeExpired = challenge?.expiresAt ? dayjs(challenge.expiresAt).valueOf() <= now : false;
  const sending = sendMutation.isPending;
  const submitting = loginMutation.isPending;

  function handleSend(): void {
    if (!isMobile(mobile)) {
      setFormError('请输入正确的 11 位手机号');
      return;
    }
    setFormError(null);
    sendMutation.mutate(mobile.trim());
  }

  function handleLogin(): void {
    if (!isMobile(mobile)) {
      setFormError('请输入正确的 11 位手机号');
      return;
    }
    if (!challenge) {
      setFormError('请先获取短信验证码');
      return;
    }
    if (challengeExpired) {
      setFormError('验证码已过期，请重新获取');
      return;
    }
    if (!isSmsCode(code)) {
      setFormError('请输入 6 位数字验证码');
      return;
    }
    if (!agreed) {
      setFormError('请先阅读并勾选同意服务协议与隐私政策');
      return;
    }
    setFormError(null);
    loginMutation.mutate({ mobile: mobile.trim(), challengeId: challenge.challengeId, code: code.trim() });
  }

  return (
    <div className={styles.page}>
      <header className={styles.brand}>
        <div className={styles.brandBar}>
          <span className={styles.brandMark} aria-hidden>M</span>
          <span className={styles.brandName}>MiniPay</span>
          <span className={styles.sandboxTag}>SANDBOX</span>
        </div>
        <div className={styles.brandCopy}>
          <p className={styles.eyebrow}>轻量、安全的数字钱包体验</p>
          <h1 className={styles.brandTitle}>把每一笔演示资金<br />都看得清清楚楚</h1>
          <p className={styles.brandSubtitle}>钱包、转账、账单与米灵助手，在一个移动端入口完成。</p>
        </div>
        <div className={styles.heroVisual} aria-hidden>
          <span className={styles.orbitOne} />
          <span className={styles.orbitTwo} />
          <div className={styles.walletGlyph}>¥</div>
        </div>
      </header>

      <section className={styles.form} aria-label="短信登录">
        <div className={styles.formHeading}>
          <div>
            <p>欢迎使用</p>
            <h2>手机号快捷登录</h2>
          </div>
          <span>新用户自动注册</span>
        </div>
        <div className={styles.field}>
          <label className={styles.fieldLabel} htmlFor="login-mobile">
            手机号
          </label>
          <div className={styles.inputShell}>
            <span className={styles.prefix}>+86</span>
            <Input
              id="login-mobile"
              type="tel"
              inputMode="numeric"
              autoComplete="tel"
              placeholder="请输入 11 位手机号"
              value={mobile}
              maxLength={11}
              onChange={(value) => setMobile(normalizeDigits(value, 11))}
            />
          </div>
        </div>

        <div className={styles.field}>
          <label className={styles.fieldLabel} htmlFor="login-code">
            短信验证码
          </label>
          <div className={`${styles.codeRow} ${styles.inputShell}`}>
            <Input
              id="login-code"
              type="tel"
              inputMode="numeric"
              autoComplete="one-time-code"
              placeholder="6 位数字验证码"
              value={code}
              maxLength={6}
              onChange={(value) => setCode(normalizeDigits(value, 6))}
            />
            <Button
              className={styles.codeButton}
              color="primary"
              fill="outline"
              loading={sending}
              disabled={sending || cooldownSeconds > 0}
              onClick={handleSend}
            >
              {cooldownSeconds > 0 ? `${cooldownSeconds}s 后重发` : '获取验证码'}
            </Button>
          </div>
        </div>

        {challenge ? (
          <p className={styles.hint}>
            演示环境验证码：{challenge.demoCode ?? DEMO_CODE}
            {challenge.expiresAt ? `（${dayjs(challenge.expiresAt).format('HH:mm:ss')} 前有效）` : ''}
          </p>
        ) : null}

        <div className={styles.agreement}>
          <Checkbox checked={agreed} onChange={setAgreed} aria-label="同意服务协议与隐私政策" />
          <span>
            我已阅读并同意
            <Link className={styles.agreementLink} to={ROUTES.serviceNotice}>
              《演示服务说明》
            </Link>
            与
            <Link className={styles.agreementLink} to={ROUTES.privacyNotice}>
              《隐私说明》
            </Link>
          </span>
        </div>

        {formError ? <InlineNotice tone="warning">{formError}</InlineNotice> : null}
        {sendMutation.isError ? <ProblemNotice error={sendMutation.error} /> : null}
        {loginMutation.isError ? <ProblemNotice error={loginMutation.error} /> : null}

        <Button className={styles.loginButton} block color="primary" size="large" loading={submitting} onClick={handleLogin}>
          安全登录
        </Button>

        <div className={styles.trustRow} aria-label="安全说明">
          <span><i aria-hidden>✓</i> HttpOnly 会话</span>
          <span><i aria-hidden>✓</i> 密码服务端校验</span>
          <span><i aria-hidden>✓</i> 沙箱资金</span>
        </div>
      </section>

      <p className={styles.footer}>
        MiniPay 沙箱演示系统 · 不连接真实银行账户
      </p>
    </div>
  );
}
