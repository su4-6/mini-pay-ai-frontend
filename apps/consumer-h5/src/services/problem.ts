import { ApiProblemError } from '@minipay/api-client';
import type { ProblemDetails } from '@minipay/api-contracts';

const UNKNOWN_PROBLEM: ProblemDetails = {
  type: 'about:blank',
  title: '操作失败，请稍后重试',
  status: 0,
  code: 'UNKNOWN',
  requestId: 'unknown'
};

/**
 * 错误分类（PROJECT_STANDARDS §6）：页面据此决定「可重试 / 需重新登录 /
 * 需人工确认 / 不可恢复」四种处理方式。
 */
export type ProblemClass = 'RETRYABLE' | 'REAUTH' | 'MANUAL_CONFIRM' | 'FATAL';

export interface ProblemView {
  problemClass: ProblemClass;
  message: string;
  status: number;
  code: string;
  requestId: string;
}

const MESSAGES: Record<string, { message: string; problemClass: ProblemClass }> = {
  // 网络与通用
  NETWORK_UNAVAILABLE: { message: '网络不可用，请检查网络后重试', problemClass: 'RETRYABLE' },
  REQUEST_TIMEOUT: { message: '请求超时，请重试', problemClass: 'RETRYABLE' },
  UNEXPECTED_RESPONSE: { message: '服务响应异常，请稍后重试', problemClass: 'RETRYABLE' },
  CSRF_RESPONSE_INVALID: { message: '安全令牌加载失败，请刷新页面重试', problemClass: 'RETRYABLE' },

  // 会话
  UNAUTHENTICATED: { message: '登录已失效，请重新登录', problemClass: 'REAUTH' },
  SESSION_EXPIRED: { message: '登录已过期，请重新登录', problemClass: 'REAUTH' },
  SESSION_INVALID: { message: '登录状态无效，请重新登录', problemClass: 'REAUTH' },
  TOKEN_INVALID: { message: '登录已失效，请重新登录', problemClass: 'REAUTH' },

  // 短信登录
  MOBILE_INVALID: { message: '请输入正确的手机号', problemClass: 'FATAL' },
  SMS_CODE_INVALID: { message: '验证码错误，请重新输入', problemClass: 'FATAL' },
  SMS_CODE_EXPIRED: { message: '验证码已过期，请重新获取', problemClass: 'FATAL' },
  SMS_CODE_LOCKED: { message: '尝试次数过多，请稍后重试', problemClass: 'MANUAL_CONFIRM' },
  SMS_RESEND_TOO_SOON: { message: '验证码发送过于频繁，请稍后重试', problemClass: 'MANUAL_CONFIRM' },
  AUTH_RATE_LIMITED: { message: '操作过于频繁，请稍后重试', problemClass: 'MANUAL_CONFIRM' },
  ACCOUNT_DISABLED: { message: '账号当前不可用，请联系客服', problemClass: 'MANUAL_CONFIRM' },

  // 支付密码
  PAY_PASSWORD_NOT_SET: { message: '尚未设置支付密码，请先设置', problemClass: 'FATAL' },
  PAY_PASSWORD_INVALID: { message: '支付密码错误，请重新输入', problemClass: 'FATAL' },
  PAYMENT_PASSWORD_INVALID: { message: '支付密码错误，请重新输入', problemClass: 'FATAL' },
  PAY_PASSWORD_LOCKED: { message: '支付密码连续错误，账号已锁定，请联系客服', problemClass: 'MANUAL_CONFIRM' },
  PAY_PASSWORD_FORMAT_INVALID: { message: '支付密码需为 6 位数字', problemClass: 'FATAL' },
  PAY_PASSWORD_WEAK: { message: '支付密码过于简单，请更换为更复杂的 6 位数字', problemClass: 'FATAL' },
  PAY_PASSWORD_ALREADY_SET: { message: '支付密码已设置，如需修改请前往账户安全', problemClass: 'FATAL' },

  // 转账
  INSUFFICIENT_BALANCE: { message: '余额不足，请先充值', problemClass: 'FATAL' },
  PAYEE_NOT_FOUND: { message: '未找到收款人，请确认收款账号或手机号', problemClass: 'FATAL' },
  TRANSFER_RECIPIENT_NOT_FOUND: {
    message: '该手机号未注册或暂不可转账，请核对后再试',
    problemClass: 'FATAL'
  },
  PAYEE_NOT_TRANSFERABLE: { message: '该收款人当前不可收款', problemClass: 'FATAL' },
  SELF_TRANSFER_NOT_ALLOWED: { message: '不能向自己转账', problemClass: 'FATAL' },
  SELF_COLLECTION_CODE: { message: '这是你自己的收款码，不能扫码付款给自己', problemClass: 'FATAL' },
  TRANSFER_INTENT_NOT_FOUND: { message: '转账意图不存在或已失效，请重新发起', problemClass: 'FATAL' },
  TRANSFER_INTENT_EXPIRED: { message: '转账确认已超时，请重新发起转账', problemClass: 'FATAL' },
  TRANSFER_INTENT_CANCELLED: { message: '该转账已取消', problemClass: 'FATAL' },
  TRANSFER_ALREADY_CONFIRMED: { message: '该转账已提交，请到转账记录查看结果', problemClass: 'MANUAL_CONFIRM' },
  TRANSFER_PROCESSING: { message: '转账处理中，请稍后在转账记录查看结果', problemClass: 'MANUAL_CONFIRM' },
  AMOUNT_LIMIT_EXCEEDED: { message: '超出单笔限额，请调整金额', problemClass: 'FATAL' },
  AMOUNT_INVALID: { message: '转账金额不合法，请重新输入', problemClass: 'FATAL' },
  /** 本地硬校验：confirm 缺少 prepare 返回的权威金额时拒绝发请求（绝不静默发 0）。 */
  TRANSFER_AMOUNT_REQUIRED: { message: '转账金额缺失或无效，请重新发起转账', problemClass: 'FATAL' },
  DAILY_LIMIT_EXCEEDED: { message: '超出当日限额，请明日再试', problemClass: 'MANUAL_CONFIRM' },
  DUPLICATE_REQUEST: { message: '请求已受理，请勿重复提交', problemClass: 'MANUAL_CONFIRM' },
  IDEMPOTENT_REPLAY: { message: '请求已受理，请勿重复提交', problemClass: 'MANUAL_CONFIRM' },
  TRANSFER_NOT_FOUND: { message: '未找到该转账单', problemClass: 'FATAL' },

  // AI
  AI_RUN_NOT_FOUND: { message: '本次对话已结束，请重新发送', problemClass: 'RETRYABLE' },
  AI_RATE_LIMITED: { message: '米灵暂时繁忙，请稍后再试', problemClass: 'RETRYABLE' },
  AI_UNAVAILABLE: { message: '米灵服务暂时不可用，请稍后重试', problemClass: 'RETRYABLE' },
  CONVERSATION_NOT_FOUND: { message: '会话不存在，请新建会话', problemClass: 'FATAL' }
};

function asProblem(error: unknown): ProblemDetails {
  return error instanceof ApiProblemError ? error.problem : UNKNOWN_PROBLEM;
}

function classFromStatus(status: number, fallback: ProblemClass): ProblemClass {
  if (status === 401) return 'REAUTH';
  if (status >= 500) return 'RETRYABLE';
  return fallback;
}

/** 把任意异常转换为页面可直接展示的可读文案 + 分类 + requestId。 */
export function describeProblem(error: unknown): ProblemView {
  const problem = asProblem(error);
  const known = MESSAGES[problem.code];
  if (known) {
    return {
      problemClass: classFromStatus(problem.status, known.problemClass),
      message: known.message,
      status: problem.status,
      code: problem.code,
      requestId: problem.requestId
    };
  }
  if (problem.status === 403) {
    return {
      problemClass: 'REAUTH',
      message: '没有权限执行该操作，请重新登录',
      status: problem.status,
      code: problem.code,
      requestId: problem.requestId
    };
  }
  if (problem.status === 404) {
    return {
      problemClass: 'FATAL',
      message: '请求的资源不存在',
      status: problem.status,
      code: problem.code,
      requestId: problem.requestId
    };
  }
  return {
    problemClass: classFromStatus(problem.status, 'FATAL'),
    message: problem.detail?.trim() || problem.title || UNKNOWN_PROBLEM.title,
    status: problem.status,
    code: problem.code,
    requestId: problem.requestId
  };
}

/** 转账单/账单的失败码中文文案（服务端只返回机器码）。 */
const FAILURE_CODE_TEXT: Record<string, string> = {
  INSUFFICIENT_BALANCE: '余额不足',
  PAYEE_ACCOUNT_CLOSED: '收款账户已关闭',
  RISK_REJECTED: '风控拦截',
  CHANNEL_TIMEOUT: '通道超时',
  TCC_CANCELLED: '资金事务已回滚',
  WALLET_UNAVAILABLE: '钱包服务不可用'
};

export function describeFailureCode(code?: string): string | undefined {
  if (!code) return undefined;
  return FAILURE_CODE_TEXT[code] ?? code;
}
