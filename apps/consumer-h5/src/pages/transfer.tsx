import { useNavigate } from '@umijs/max';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Dialog, Input, Toast } from 'antd-mobile';
import { useState } from 'react';
import type { CSSProperties } from 'react';
import { AmountText } from '../components/AmountText';
import { AppShell } from '../components/AppShell';
import { AuthGate } from '../components/AuthGate';
import { Card } from '../components/Card';
import { InlineNotice } from '../components/InlineNotice';
import { KeyValueRow } from '../components/KeyValueRow';
import { PayPasswordField } from '../components/PayPasswordField';
import { ProblemNotice } from '../components/ProblemNotice';
import { ROUTES } from '../constants/routes';
import { useNow } from '../hooks/useNow';
import { useSession } from '../hooks/useSession';
import { queryKeys } from '../query/keys';
import { describeProblem } from '../services/problem';
import { cancelTransfer, confirmTransfer, prepareTransfer } from '../services/transfers';
import { useTransferStore } from '../stores/transfer-intent';
import { isExpired, remainingLabel } from '../utils/datetime';
import { fenToYuanInput, formatFenWithSymbol, sanitizeAmountInput, validateAmountInput } from '../utils/money';
import { isPayPassword } from '../utils/validators';
import styles from './transfer.module.less';

const AMOUNT_ERROR_TEXT: Record<string, string> = {
  EMPTY: '请输入转账金额',
  FORMAT: '金额格式不正确，最多两位小数',
  TOO_SMALL: '单笔转账金额至少 0.01 元',
  TOO_LARGE: '超出单笔转账上限（¥10,000.00）'
};

type Step = 'form' | 'confirm';

function TransferWorkspace() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { profile, payPasswordSet } = useSession();
  const realNameVerified = profile?.realNameVerified ?? false;
  const fundsReady = realNameVerified && payPasswordSet;
  const prepared = useTransferStore((state) => state.prepared);
  const transferDraft = useTransferStore((state) => state.draft);
  const setPrepared = useTransferStore((state) => state.setPrepared);
  const clearDraft = useTransferStore((state) => state.clearDraft);
  const clearPrepared = useTransferStore((state) => state.clear);

  const now = useNow(1_000);
  const [step, setStep] = useState<Step>(prepared ? 'confirm' : 'form');

  // 收款人必须由用户在转账页主动填写，避免 AI 文本或历史草稿把脱敏号码
  // 误当成真实手机号提交，也避免用户未核对就向预填对象转账。
  const [payeeIdentifier, setPayeeIdentifier] = useState('');
  const [remark, setRemark] = useState('');
  const [amountInput, setAmountInput] = useState(
    transferDraft ? fenToYuanInput(transferDraft.amountFen) : ''
  );
  const [amountError, setAmountError] = useState<string | null>(null);
  // 支付密码只存在于组件状态，提交后立刻清空；不落任何持久化与 URL。
  const [paymentPassword, setPaymentPassword] = useState('');

  const prepareMutation = useMutation({
    mutationFn: prepareTransfer,
    onSuccess: (intent) => {
      clearDraft();
      setPrepared(intent);
      setStep('confirm');
      setPaymentPassword('');
    }
  });

  const confirmMutation = useMutation({
    mutationFn: (input: { transferIntentId: string; paymentPassword: string; amountFen: number }) =>
      confirmTransfer(input.transferIntentId, input.paymentPassword, input.amountFen),
    onSuccess: (result) => {
      // 提交成功：立即清空密码与本地意图，并按精确 key 失效钱包与转账缓存。
      setPaymentPassword('');
      clearPrepared();
      setStep('form');
      setAmountInput('');
      setRemark('');
      void queryClient.invalidateQueries({ queryKey: queryKeys.walletRoot });
      void queryClient.invalidateQueries({ queryKey: queryKeys.transfersRoot });
      navigate(`${ROUTES.transferResult}?transferNo=${encodeURIComponent(result.transferNo)}`, {
        replace: true
      });
    },
    onError: () => {
      setPaymentPassword('');
    }
  });

  const cancelMutation = useMutation({
    mutationFn: (transferIntentId: string) => cancelTransfer(transferIntentId),
    onSuccess: () => {
      clearPrepared();
      setStep('form');
      setPaymentPassword('');
      Toast.show({ content: '已取消本次转账' });
    },
    onError: (error) => {
      // 意图已过期/不存在时服务端会返回 404：本地同样视为已释放，回到填单步骤。
      const code = describeProblem(error).code;
      if (code === 'TRANSFER_INTENT_NOT_FOUND' || code === 'TRANSFER_INTENT_EXPIRED' || code === 'HTTP_404') {
        clearPrepared();
        setStep('form');
        setPaymentPassword('');
        Toast.show({ content: '转账意图已失效，请重新发起' });
      }
    }
  });

  const expired = isExpired(prepared?.expiresAt, now);

  function handlePrepare(): void {
    const payee = payeeIdentifier.trim();
    if (!payee) {
      setAmountError('请输入收款人手机号或 MiniPay 号');
      return;
    }
    const validation = validateAmountInput(amountInput);
    if (!validation.ok) {
      setAmountError(AMOUNT_ERROR_TEXT[validation.reason] ?? '金额不合法');
      return;
    }
    setAmountError(null);
    prepareMutation.mutate({
      payeeIdentifier: payee,
      amountFen: validation.fen,
      ...(remark.trim() ? { remark: remark.trim() } : {})
    });
  }

  async function handleConfirm(): Promise<void> {
    if (!prepared) return;
    if (expired) return;
    if (!isPayPassword(paymentPassword)) {
      Toast.show({ content: '请输入 6 位数字支付密码' });
      return;
    }
    // 资金操作二次确认：金额与收款方必须由用户显式确认。
    const confirmed = await Dialog.confirm({
      title: '确认转账',
      content: `确认向 ${prepared.payeeMasked} 转账 ${formatFenWithSymbol(prepared.amountFen)} 吗？确认后资金将立即从余额扣除。`,
      confirmText: '确认转账',
      cancelText: '再想想'
    });
    if (!confirmed) return;
    confirmMutation.mutate({
      transferIntentId: prepared.transferIntentId,
      paymentPassword,
      // 金额必须取 prepare 返回的意图权威值：一次性支付授权令牌与 intentId + 金额绑定，
      // 这里绝不能用输入框里的本地 amountInput（用户可改，且可能已与意图不一致）。
      amountFen: prepared.amountFen
    });
  }

  const passwordNotSet =
    confirmMutation.isError &&
    (confirmMutation.error instanceof Error
      ? describeProblem(confirmMutation.error).code === 'PAY_PASSWORD_NOT_SET'
      : false);

  return (
    <AppShell
      title={step === 'form' ? '转账' : '确认转账'}
      subtitle={
        step === 'form'
          ? '先由服务端校验收款人并生成转账意图，再输入支付密码确认'
          : '请核对收款方与金额，确认后将立即扣款'
      }
      backTo={step === 'confirm' ? undefined : ROUTES.home}
      showTabBar={false}
    >
      {step === 'form' ? (
        <Card>
          <div className={styles.form}>
            <div className={styles.field}>
              <label className={styles.fieldLabel} htmlFor="transfer-payee">
                收款人手机号 / MiniPay 号
              </label>
              <Input
                id="transfer-payee"
                placeholder="例如 13800000000"
                value={payeeIdentifier}
                maxLength={32}
                onChange={setPayeeIdentifier}
              />
            </div>

            <div className={styles.field}>
              <label className={styles.fieldLabel} htmlFor="transfer-amount">
                转账金额（元）
              </label>
              <div className={styles.amountRow}>
                <span className={styles.amountSymbol} aria-hidden>
                  ¥
                </span>
                <Input
                  id="transfer-amount"
                  style={{ '--font-size': '24px', fontWeight: 700 } as CSSProperties}
                  type="text"
                  inputMode="decimal"
                  placeholder="0.00"
                  value={amountInput}
                  onChange={(value) => {
                    setAmountInput(sanitizeAmountInput(value));
                    setAmountError(null);
                  }}
                />
              </div>
              <p className={styles.stepHint}>
                金额按「元」输入，提交前会转换为整数「分」；服务端为最终权威金额。
              </p>
            </div>

            <div className={styles.field}>
              <label className={styles.fieldLabel} htmlFor="transfer-remark">
                备注（可选）
              </label>
              <Input id="transfer-remark" placeholder="最多 50 字" value={remark} maxLength={50} onChange={setRemark} />
            </div>

            {amountError ? <InlineNotice tone="warning">{amountError}</InlineNotice> : null}
            {prepareMutation.isError ? <ProblemNotice error={prepareMutation.error} /> : null}

            {!realNameVerified ? (
              <InlineNotice tone="warning">
                请先完成沙箱实名认证，再设置支付密码后使用转账。
                <Button size="mini" color="primary" fill="none" onClick={() => navigate(ROUTES.realName)}>
                  去实名认证
                </Button>
              </InlineNotice>
            ) : !payPasswordSet ? (
              <InlineNotice tone="warning">
                尚未设置支付密码，无法完成转账。
                <Button size="mini" color="primary" fill="none" onClick={() => navigate(ROUTES.payPassword)}>
                  去设置支付密码
                </Button>
              </InlineNotice>
            ) : null}

            <Button
              block
              color="primary"
              size="large"
              loading={prepareMutation.isPending}
              disabled={!fundsReady}
              onClick={handlePrepare}
            >
              下一步：确认转账
            </Button>
          </div>
        </Card>
      ) : null}

      {step === 'confirm' && prepared ? (
        <div>
          <div className={styles.summary} aria-label="转账信息确认">
            <span className={styles.summaryPayee}>转账给 {prepared.payeeMasked}</span>
            <span className={styles.summaryAmount}>{formatFenWithSymbol(prepared.amountFen)}</span>
            <span className={styles.countdown}>{remainingLabel(prepared.expiresAt, now)}</span>
          </div>

          <Card tight>
            <dl style={{ margin: 0 }}>
              <KeyValueRow label="收款人" value={prepared.payeeMasked} />
              <KeyValueRow label="金额" value={<AmountText fen={prepared.amountFen} size="sm" />} />
              {prepared.remark ? <KeyValueRow label="备注" value={prepared.remark} /> : null}
              <KeyValueRow label="转账意图" value={prepared.transferIntentId} mono />
            </dl>
          </Card>

          {expired ? (
            <InlineNotice tone="warning">
              已超过服务端返回的确认有效期，无法继续提交。为避免重复创建转账意图，请取消后重新发起。
            </InlineNotice>
          ) : null}

          {passwordNotSet ? (
            <InlineNotice tone="warning">
              服务端返回尚未设置支付密码，请先前往设置。
              <Button size="mini" color="primary" fill="none" onClick={() => navigate(ROUTES.payPassword)}>
                去设置
              </Button>
            </InlineNotice>
          ) : null}

          <Card title="输入支付密码">
            <PayPasswordField
              value={paymentPassword}
              onChange={setPaymentPassword}
              disabled={expired || confirmMutation.isPending}
              autoFocus
            />
            {confirmMutation.isError && !passwordNotSet ? (
              <ProblemNotice error={confirmMutation.error} />
            ) : null}
            <div className={styles.form} style={{ marginTop: 12 }}>
              <Button
                block
                color="primary"
                size="large"
                loading={confirmMutation.isPending}
                disabled={expired || !isPayPassword(paymentPassword)}
                onClick={() => void handleConfirm()}
              >
                确认转账
              </Button>
              <Button
                block
                fill="outline"
                loading={cancelMutation.isPending}
                onClick={() => {
                  if (prepared) cancelMutation.mutate(prepared.transferIntentId);
                }}
              >
                取消本次转账并返回修改
              </Button>
              <p className={styles.stepHint}>
                「取消」会调用 DELETE 释放本次转账意图；输入过的收款人与金额仍保留在填单页，可修改后重新发起。
              </p>
            </div>
          </Card>

          {cancelMutation.isError ? <ProblemNotice error={cancelMutation.error} /> : null}

          <InlineNotice>
            转账为资金操作：确认前请再次核对收款方与金额。若提交后结果未知，
            请到「转账记录」按转账单号查询，切勿重复发起。
          </InlineNotice>
        </div>
      ) : null}

      {step === 'confirm' && !prepared ? (
        <InlineNotice tone="warning">
          转账意图已丢失（页面可能被刷新）。请返回重新发起转账。
          <Button size="mini" color="primary" fill="none" onClick={() => setStep('form')}>
            返回转账
          </Button>
        </InlineNotice>
      ) : null}
    </AppShell>
  );
}

export default function TransferPage() {
  return (
    <AuthGate>
      <TransferWorkspace />
    </AuthGate>
  );
}
