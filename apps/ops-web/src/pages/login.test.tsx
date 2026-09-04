import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LoginPage from './login';
import { createCaptcha, sendSmsChallenge, submitLogin } from '../services/auth';

vi.mock('../services/auth', () => ({
  createCaptcha: vi.fn(),
  sendSmsChallenge: vi.fn(),
  submitLogin: vi.fn()
}));

const mockedCreateCaptcha = vi.mocked(createCaptcha);
const mockedSendSmsChallenge = vi.mocked(sendSmsChallenge);
const mockedSubmitLogin = vi.mocked(submitLogin);

function renderPage() {
  return render(
    <ConfigProvider locale={zhCN}>
      <App>
        <LoginPage />
      </App>
    </ConfigProvider>
  );
}

beforeEach(() => {
  // 注意：不要调用 vi.restoreAllMocks()——它会清掉 setup.ts 里的全局
  // matchMedia/ResizeObserver mock，导致 antd responsiveObserver 崩溃。
  mockedCreateCaptcha.mockReset().mockResolvedValue({
    captchaId: 'cap-1',
    imageUrl: '/api/v1/auth/captchas/cap-1/image',
    expiresAt: '2026-08-07T08:00:00Z'
  });
  mockedSendSmsChallenge.mockReset().mockResolvedValue({
    challengeId: 'challenge-1',
    maskedPhone: '138****0000',
    expiresAt: '2026-08-07T08:01:00Z',
    resendAfterSeconds: 60
  });
  mockedSubmitLogin.mockReset().mockResolvedValue({
    redirectUrl: 'http://localhost:8081/oauth2/authorize?client_id=ops'
  });
  Object.defineProperty(window, 'location', {
    configurable: true,
    value: {
      href: 'http://localhost/login',
      origin: 'http://localhost',
      pathname: '/login',
      search: '',
      hash: '',
      assign: vi.fn(),
      replace: vi.fn(),
      reload: vi.fn()
    }
  });
});

describe('OpsLoginPage', () => {
  it('renders brand panel and loads a captcha', async () => {
    renderPage();
    expect(screen.getByText('面向运营的一站式商户管理平台')).toBeTruthy();
    expect(screen.getByPlaceholderText('请输入管理员手机号').getAttribute('maxlength')).toBe('11');
    await waitFor(() => expect(mockedCreateCaptcha).toHaveBeenCalledTimes(1));
    expect(screen.getByAltText('图形验证码，点击刷新')).toBeTruthy();
  });

  it('submits password login and redirects on success', async () => {
    const assign = window.location.assign as ReturnType<typeof vi.fn>;
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByPlaceholderText('请输入管理员手机号'), '13800000000');
    await user.type(screen.getByPlaceholderText('请输入登录密码'), 'secret-123');
    await user.type(screen.getByPlaceholderText('请输入验证码'), '1234');
    await user.click(screen.getByRole('button', { name: /登\s*录/ }));

    await waitFor(() =>
      expect(mockedSubmitLogin).toHaveBeenCalledWith('/login/password', {
        phone: '13800000000',
        password: 'secret-123',
        captchaId: 'cap-1',
        captchaCode: '1234'
      })
    );
    expect(assign).toHaveBeenCalledWith('http://localhost:8081/oauth2/authorize?client_id=ops');
  });

  it('shows an error alert when password login is rejected', async () => {
    mockedSubmitLogin.mockRejectedValue(new Error('登录失败，请检查手机号、密码或验证码'));
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByPlaceholderText('请输入管理员手机号'), '13800000000');
    await user.type(screen.getByPlaceholderText('请输入登录密码'), 'wrong');
    await user.type(screen.getByPlaceholderText('请输入验证码'), '0000');
    await user.click(screen.getByRole('button', { name: /登\s*录/ }));

    await waitFor(() =>
      expect(screen.getByText('登录失败，请检查手机号、密码或验证码')).toBeTruthy()
    );
  });

  it('switches to SMS login and sends a challenge', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole('tab', { name: '验证码登录' }));
    await user.type(screen.getByPlaceholderText('请输入管理员手机号'), '13800000000');
    await user.type(screen.getByPlaceholderText('请输入验证码'), '1234');
    await user.click(screen.getByRole('button', { name: '获取短信验证码' }));

    await waitFor(() =>
      expect(mockedSendSmsChallenge).toHaveBeenCalledWith('13800000000', 'cap-1', '1234')
    );
    expect(screen.getByPlaceholderText('请输入 6 位验证码')).toBeTruthy();
  });

  it('keeps the login spinner independent while an SMS challenge is pending', async () => {
    let resolveChallenge!: (value: Awaited<ReturnType<typeof sendSmsChallenge>>) => void;
    mockedSendSmsChallenge.mockReturnValue(new Promise((resolve) => {
      resolveChallenge = resolve;
    }));
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole('tab', { name: '验证码登录' }));
    await user.type(screen.getByPlaceholderText('请输入管理员手机号'), '13800000000');
    await user.type(screen.getByPlaceholderText('请输入验证码'), '1234');

    const sendButton = screen.getByRole('button', { name: '获取短信验证码' });
    const loginButton = screen.getByRole('button', { name: /登\s*录/ });
    await user.click(sendButton);

    await waitFor(() => expect(sendButton.classList.contains('ant-btn-loading')).toBe(true));
    expect(loginButton.classList.contains('ant-btn-loading')).toBe(false);

    resolveChallenge({
      challengeId: 'challenge-1',
      maskedPhone: '138****0000',
      expiresAt: '2026-08-07T08:01:00Z',
      resendAfterSeconds: 60
    });
    await waitFor(() => expect(screen.getByPlaceholderText('请输入 6 位验证码')).toBeTruthy());
  });
});
