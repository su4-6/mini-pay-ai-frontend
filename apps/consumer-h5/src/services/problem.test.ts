import { describe, expect, it } from 'vitest';
import { ApiProblemError } from '@minipay/api-client';
import type { ProblemDetails } from '@minipay/api-contracts';
import { describeFailureCode, describeProblem } from './problem';

function problem(overrides: Partial<ProblemDetails>): ApiProblemError {
  return new ApiProblemError({
    type: 'about:blank',
    title: 'Request failed',
    status: 400,
    code: 'UNKNOWN',
    requestId: 'req-1',
    ...overrides
  });
}

describe('Problem Details 错误映射', () => {
  it('网络与 5xx 归为可重试', () => {
    expect(describeProblem(problem({ code: 'NETWORK_UNAVAILABLE', status: 0 })).problemClass).toBe(
      'RETRYABLE'
    );
    expect(describeProblem(problem({ code: 'ANY', status: 503 })).problemClass).toBe('RETRYABLE');
  });

  it('401 / 403 归为需重新认证', () => {
    expect(describeProblem(problem({ code: 'ANY', status: 401 })).problemClass).toBe('REAUTH');
    expect(describeProblem(problem({ code: 'ANY', status: 403 })).problemClass).toBe('REAUTH');
  });

  it('资金处理中类归为需人工确认', () => {
    expect(describeProblem(problem({ code: 'DUPLICATE_REQUEST' })).problemClass).toBe('MANUAL_CONFIRM');
    expect(describeProblem(problem({ code: 'TRANSFER_ALREADY_CONFIRMED' })).problemClass).toBe(
      'MANUAL_CONFIRM'
    );
  });

  it('业务校验类归为不可恢复，并给出可读文案', () => {
    const view = describeProblem(problem({ code: 'SMS_CODE_INVALID' }));
    expect(view.problemClass).toBe('FATAL');
    expect(view.message).toBe('验证码错误，请重新输入');
  });

  it('保留 requestId 供客服定位', () => {
    const view = describeProblem(problem({ code: 'SMS_CODE_EXPIRED', requestId: 'req-abc' }));
    expect(view.requestId).toBe('req-abc');
  });

  it('未知异常退化为默认文案而不是抛错', () => {
    const view = describeProblem(new Error('boom'));
    expect(view.code).toBe('UNKNOWN');
    expect(view.message).toBe('操作失败，请稍后重试');
  });

  it('失败码有中文文案时做映射', () => {
    expect(describeFailureCode('INSUFFICIENT_BALANCE')).toBe('余额不足');
    expect(describeFailureCode('SOME_NEW_CODE')).toBe('SOME_NEW_CODE');
    expect(describeFailureCode(undefined)).toBeUndefined();
  });
});
