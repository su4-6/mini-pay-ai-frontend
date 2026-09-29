import { useNavigate } from '@umijs/max';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Dialog, Input, Toast } from 'antd-mobile';
import { ScanningOutline } from 'antd-mobile-icons';
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
import { ScanCodeButton } from '../components/ScanCodeButton';
import { ROUTES } from '../constants/routes';
import { useNow } from '../hooks/useNow';
import { useSession } from '../hooks/useSession';
import { queryKeys } from '../query/keys';
import {
  confirmMerchantPayment,
  isMerchantResolution,
  isPersonalResolution,
  prepareMerchantPayment,
  scanCollectionCode
} from '../services/payments';
import { confirmTransfer, prepareTransferFromCollectionCode } from '../services/transfers';
import { describeProblem } from '../services/problem';
import type { CollectionResolution, PreparedPayment, PreparedTransfer } from '../types/consumer';
import { isExpired, remainingLabel } from '../utils/datetime';
import { formatFenWithSymbol, sanitizeAmountInput, validateAmountInput } from '../utils/money';
import { extractPaymentCode } from '../utils/scan';
import { isPayPassword } from '../utils/validators';
import styles from './pay.module.less';

const AMOUNT_ERROR_TEXT: Record<string, string> = {
  EMPTY: '请输入付款金额',
  FORMAT: '金额格式不正确，最多两位小数',
  TOO_SMALL: '单笔付款金额至少 0.01 元',
  TOO_LARGE: '超出单笔付款上限（¥10,000.00）'
};

type Step = 'form' | 'confirm';

/**
 * 扫个人或商户收款码：个人码进入站内转账，商户码进入支付单；两条链路都需支付密码确认。
 * 金额与支付状态一律以后端为准；H5 只负责展示与把权威金额原样回传。
 */
function PayWorkspace() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { profile, payPasswordSet } = useSession();
  const realNameVerified = profile?.realNameVerified ?? false;
  const fundsReady = realNameVerified && payPasswordSet;
  const now = useNow(1_000);

  const [step, setStep] = useState<Step>('form');
  const [codeInput, setCodeInput] = useState('');
  const [resolution, setResolution] = useState<CollectionResolution | null>(null);
  const [amountInput, setAmountInput] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [prepared, setPrepared] = useState<PreparedPayment | null>(null);
  const [preparedTransfer, setPreparedTransfer] = useState<PreparedTransfer | null>(null);
  // 支付密码只存在于组件状态，提交后立刻清空；不落任何持久化与 URL。
  const [paymentPassword, setPaymentPassword] = useState('');
  const [result, setResult] = useState<{ orderNo: string; status: string; kind: 'merchant' | 'personal' } | null>(null);

  const scanMutation = useMutation({
    mutationFn: scanCollectionCode,
    onSuccess: (value) => {
      setResolution(value);
      setFormError(null);
    }
  });

  const prepareMutation = useMutation({
    mutationFn: prepareMerchantPayment,
    onSuccess: (order) => {
      setPrepared(order);
      setStep('confirm');
      setPaymentPassword('');
    }
  });

  const confirmMutation = useMutation({
    mutationFn: (input: { paymentOrderId: string; paymentPassword: string; amountFen: number }) =>
      confirmMerchantPayment(input.paymentOrderId, input.paymentPassword, input.amountFen),
    onSuccess: (confirmation) => {
      setPaymentPassword('');
      setResult({ orderNo: confirmation.paymentOrderNo, status: confirmation.status, kind: 'merchant' });
      // 付款会改余额与账单，因此按精确 key 失效钱包与转账缓存。
      void queryClient.invalidateQueries({ queryKey: queryKeys.walletRoot });
      void queryClient.invalidateQueries({ queryKey: queryKeys.transfersRoot });
    },
    onError: () => {
      setPaymentPassword('');
    }
  });

  const prepareTransferMutation = useMutation({
    mutationFn: prepareTransferFromCollectionCode,
    onSuccess: (intent) => {
      setPreparedTransfer(intent);
      setStep('confirm');
      setPaymentPassword('');
    }
  });

  const confirmTransferMutation = useMutation({
    mutationFn: (input: { transferIntentId: string; paymentPassword: string; amountFen: number }) =>
      confirmTransfer(input.transferIntentId, input.paymentPassword, input.amountFen),
    onSuccess: (confirmation) => {
      setPaymentPassword('');
      setResult({ orderNo: confirmation.transferNo, status: confirmation.status, kind: 'personal' });
      void queryClient.invalidateQueries({ queryKey: queryKeys.walletRoot });
      void queryClient.invalidateQueries({ queryKey: queryKeys.transfersRoot });
    },
    onError: () => setPaymentPassword('')
  });

  const activePrepared = prepared ?? preparedTransfer;
  const expired = isExpired(activePrepared?.expiresAt, now);
  const merchantReady = isMerchantResolution(resolution);
  const personalReady = isPersonalResolution(resolution);
  const receiverReady = merchantReady || personalReady;

  function handleScan(rawOverride?: string): void {
    // 相机扫到的原文与手输/粘贴的内容走同一条解析：允许深链、裸令牌、带 token 的链接。
    const code = extractPaymentCode(rawOverride ?? codeInput);
    if (!code) {
      setFormError('请扫码或粘贴有效的个人或商户收款码内容（minipay://collect/...）');
      return;
    }
    setCodeInput(code);
    setResolution(null);
    setPrepared(null);
    setPreparedTransfer(null);
    setResult(null);
    setFormError(null);
    scanMutation.mutate(code);
  }

  function handlePrepare(): void {
    if (!resolution) return;
    if (!receiverReady) {
      setFormError('请先识别一个有效的个人或商户收款码');
      return;
    }
    const validation = validateAmountInput(amountInput);
    if (!validation.ok) {
      setFormError(AMOUNT_ERROR_TEXT[validation.reason] ?? '金额不合法');
      return;
    }
    setFormError(null);
    if (merchantReady) {
      prepareMutation.mutate({ resolution, amountFen: validation.fen });
    } else {
      prepareTransferMutation.mutate({
        deepLink: codeInput,
        amountFen: validation.fen,
        remark: '扫码转账'
      });
    }
  }

  async function handleConfirm(): Promise<void> {
    if (!activePrepared || expired) return;
    if (!isPayPassword(paymentPassword)) {
      Toast.show({ content: '请输入 6 位数字支付密码' });
      return;
    }
    // 资金操作二次确认：收款方与金额必须由用户显式确认。
    const confirmed = await Dialog.confirm({
      title: personalReady ? '确认转账' : '确认付款',
      content: `确认向 ${personalReady ? preparedTransfer?.payeeMasked : prepared?.merchantName} ${personalReady ? '转账' : '付款'} ${formatFenWithSymbol(activePrepared.amountFen)} 吗？确认后资金将立即从余额扣除。`,
      confirmText: personalReady ? '确认转账' : '确认付款',
      cancelText: '再想想'
    });
    if (!confirmed) return;
    if (preparedTransfer) {
      confirmTransferMutation.mutate({
        transferIntentId: preparedTransfer.transferIntentId,
        paymentPassword,
        amountFen: preparedTransfer.amountFen
      });
    } else if (prepared) {
      confirmMutation.mutate({
        paymentOrderId: prepared.paymentOrderId,
        paymentPassword,
        amountFen: prepared.amountFen
      });
    }
  }

  function resetFlow(): void {
    setStep('form');
    setPrepared(null);
    setPreparedTransfer(null);
    setResult(null);
    setPaymentPassword('');
    setAmountInput('');
    setResolution(null);
    setCodeInput('');
  }

  const passwordNotSet =
    (confirmMutation.isError || confirmTransferMutation.isError) &&
    ((confirmMutation.error ?? confirmTransferMutation.error) instanceof Error
      ? describeProblem((confirmMutation.error ?? confirmTransferMutation.error) as Error).code === 'PAY_PASSWORD_NOT_SET'
      : false);

  if (result) {
    return (
      <AppShell title="付款结果" subtitle="结果以后端支付单为准" backTo={ROUTES.home} showTabBar={false}>
        <div className={styles.summary} aria-label="付款结果">
          <span className={styles.summaryPayee}>
            {result.status === 'SUCCEEDED' || result.status === 'SUCCESS' ? '付款成功' : '付款处理中'}
          </span>
          <span className={styles.summaryAmount}>{formatFenWithSymbol(activePrepared?.amountFen ?? 0)}</span>
          <span className={styles.countdown}>
            {result.status === 'PROCESSING' ? '结果未知时请勿重复支付，稍后到账单里核对' : '余额与账单已更新'}
          </span>
        </div>

        <Card tight>
          <dl style={{ margin: 0 }}>
            <KeyValueRow label="收款方" value={preparedTransfer?.payeeMasked ?? prepared?.merchantName ?? '收款方'} />
            <KeyValueRow label="金额" value={<AmountText fen={activePrepared?.amountFen ?? 0} size="sm" />} />
            <KeyValueRow label={result.kind === 'personal' ? '转账单号' : '支付单号'} value={result.orderNo} mono />
            <KeyValueRow label="状态" value={result.status} />
          </dl>
        </Card>

        <div className={styles.form} style={{ marginTop: 12 }}>
          <Button block color="primary" size="large" onClick={() => navigate(ROUTES.home, { replace: true })}>
            完成并返回首页
          </Button>
          <Button block fill="outline" onClick={resetFlow}>
            再付一笔
          </Button>
        </div>
      </AppShell>
    );
  }

  return (
    <AppShell
      title={step === 'form' ? '扫码付款' : '确认付款'}
      subtitle={
        step === 'form'
          ? '扫一扫识别个人或商户收款码，再核对收款方与金额'
          : '请核对收款方与金额，确认后将立即扣款'
      }
      backTo={step === 'confirm' ? undefined : ROUTES.home}
      showTabBar={false}
    >
      {step === 'form' ? (
        <><section className={styles.scanHero}>
          <ScanningOutline className={styles.scanHeroIcon} />
          <div><strong>扫描 MiniPay 收款码</strong><span>支持个人转账码和商户收款码</span></div>
        </section><Card>
          <div className={styles.form}>
            <div className={styles.field}>
              <label className={styles.fieldLabel} htmlFor="pay-code">
                个人 / 商户收款码
              </label>
              <Input
                id="pay-code"
                placeholder="扫码或粘贴 minipay://collect/..."
                value={codeInput}
                onChange={(value) => {
                  setCodeInput(value);
                  setFormError(null);
                }}
              />
              <p className={styles.stepHint}>
                个人码走站内转账，商户码走商户付款；两种流程都需要核对金额并输入支付密码。
              </p>
              <ScanCodeButton
                disabled={scanMutation.isPending}
                onDetected={(text) => handleScan(text)}
              />
              <Button
                block
                fill="outline"
                loading={scanMutation.isPending}
                disabled={!codeInput.trim()}
                onClick={() => handleScan()}
              >
                识别收款方
              </Button>
            </div>

            {scanMutation.isError ? <ProblemNotice error={scanMutation.error} /> : null}

            {merchantReady && resolution ? (
              <div className={styles.merchant}>
                <span className={styles.merchantName}>{resolution.merchantName ?? '商户'}</span>
                <span className={styles.countdown}>商户收款码</span>
              </div>
            ) : null}

            {personalReady && resolution ? (
              <div className={styles.merchant}>
                <span className={styles.merchantName}>{resolution.receiverDisplay ?? '个人用户'}</span>
                <span className={styles.countdown}>个人收款码 · 站内转账</span>
              </div>
            ) : null}

            {receiverReady ? (
              <div className={styles.field}>
                <label className={styles.fieldLabel} htmlFor="pay-amount">
                  付款金额（元）
                </label>
                <div className={styles.amountRow}>
                  <span className={styles.amountSymbol} aria-hidden>
                    ¥
                  </span>
                  <Input
                    id="pay-amount"
                    style={{ '--font-size': '24px', fontWeight: 700 } as CSSProperties}
                    type="text"
                    inputMode="decimal"
                    placeholder="0.00"
                    value={amountInput}
                    onChange={(value) => {
                      setAmountInput(sanitizeAmountInput(value));
                      setFormError(null);
                    }}
                  />
                </div>
                <p className={styles.stepHint}>金额按「元」输入，提交前转换为整数「分」；服务端为最终权威金额。</p>
              </div>
            ) : null}

            {formError ? <InlineNotice tone="warning">{formError}</InlineNotice> : null}
            {prepareMutation.isError ? <ProblemNotice error={prepareMutation.error} /> : null}
            {prepareTransferMutation.isError ? <ProblemNotice error={prepareTransferMutation.error} /> : null}

            {!realNameVerified ? (
              <InlineNotice tone="warning">
                请先完成沙箱实名认证，再设置支付密码后使用付款。
                <Button size="mini" color="primary" fill="none" onClick={() => navigate(ROUTES.realName)}>
                  去实名认证
                </Button>
              </InlineNotice>
            ) : !payPasswordSet ? (
              <InlineNotice tone="warning">
                尚未设置支付密码，无法完成付款。
                <Button size="mini" color="primary" fill="none" onClick={() => navigate(ROUTES.payPassword)}>
                  去设置支付密码
                </Button>
              </InlineNotice>
            ) : null}

            <Button
              block
              color="primary"
              size="large"
              loading={prepareMutation.isPending || prepareTransferMutation.isPending}
              disabled={!fundsReady || !receiverReady}
              onClick={handlePrepare}
            >
              下一步：确认{personalReady ? '转账' : '付款'}
            </Button>
            <p className={styles.stepHint}>
              个人收款码不会创建商户订单，会按同一钱包的站内转账规则入账。
            </p>
          </div>
        </Card></>
      ) : null}

      {step === 'confirm' && activePrepared ? (
        <div>
          <div className={styles.summary} aria-label="付款信息确认">
            <span className={styles.summaryPayee}>{preparedTransfer ? '转账给' : '付款给'} {preparedTransfer?.payeeMasked ?? prepared?.merchantName}</span>
            <span className={styles.summaryAmount}>{formatFenWithSymbol(activePrepared.amountFen)}</span>
            <span className={styles.countdown}>{remainingLabel(activePrepared.expiresAt, now)}</span>
          </div>

          <Card tight>
            <dl style={{ margin: 0 }}>
              <KeyValueRow label="收款方" value={preparedTransfer?.payeeMasked ?? prepared?.merchantName ?? '收款方'} />
              <KeyValueRow label="金额" value={<AmountText fen={activePrepared.amountFen} size="sm" />} />
              {prepared?.paymentOrderNo ? (
                <KeyValueRow label="支付单号" value={prepared.paymentOrderNo} mono />
              ) : null}
              <KeyValueRow label={preparedTransfer ? '转账意图' : '支付单'} value={preparedTransfer?.transferIntentId ?? prepared?.paymentOrderId ?? ''} mono />
            </dl>
          </Card>

          {expired ? (
            <InlineNotice tone="warning">
              已超过服务端返回的支付有效期，无法继续提交。请返回重新识别收款码发起付款。
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
              disabled={expired || confirmMutation.isPending || confirmTransferMutation.isPending}
              autoFocus
            />
            {(confirmMutation.isError || confirmTransferMutation.isError) && !passwordNotSet ? (
              <ProblemNotice error={confirmMutation.error ?? confirmTransferMutation.error} />
            ) : null}
            <div className={styles.form} style={{ marginTop: 12 }}>
              <Button
                block
                color="primary"
                size="large"
                loading={confirmMutation.isPending || confirmTransferMutation.isPending}
                disabled={expired || !isPayPassword(paymentPassword)}
                onClick={() => void handleConfirm()}
              >
                确认{preparedTransfer ? '转账' : '付款'}
              </Button>
              <Button block fill="outline" onClick={resetFlow}>
                取消并返回重填
              </Button>
            </div>
          </Card>

          <InlineNotice>
            付款为资金操作：确认前请再次核对收款方与金额。若提交后结果未知，
            请到「账单」核对，切勿重复付款。
          </InlineNotice>
        </div>
      ) : null}

      {step === 'confirm' && !activePrepared ? (
        <InlineNotice tone="warning">
          支付单已丢失（页面可能被刷新）。请返回重新识别收款码。
          <Button size="mini" color="primary" fill="none" onClick={resetFlow}>
            返回付款
          </Button>
        </InlineNotice>
      ) : null}
    </AppShell>
  );
}

export default function PayPage() {
  return (
    <AuthGate>
      <PayWorkspace />
    </AuthGate>
  );
}
