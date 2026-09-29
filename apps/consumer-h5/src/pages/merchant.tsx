import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Input, TextArea, Toast } from 'antd-mobile';
import { useMemo, useState } from 'react';
import { AppShell } from '../components/AppShell';
import { AsyncState } from '../components/AsyncState';
import { AuthGate } from '../components/AuthGate';
import { Card } from '../components/Card';
import { InlineNotice } from '../components/InlineNotice';
import { ProblemNotice } from '../components/ProblemNotice';
import { ROUTES } from '../constants/routes';
import { queryKeys } from '../query/keys';
import {
  fetchMerchantCenter,
  initializeMerchant,
  resubmitMerchantOnboarding,
  submitMerchantOnboarding,
  uploadMerchantImage
} from '../services/merchant';
import type { MerchantOnboardingInput } from '../services/merchant';
import { formatDateTime } from '../utils/datetime';
import styles from './merchant.module.less';

const STATUS: Record<string, string> = {
  PENDING: '审核中',
  APPROVED: '已通过',
  REJECTED: '未通过',
  SUPPLEMENT: '待补充',
  ACTIVE: '正常',
  FROZEN: '已冻结',
  DISABLED: '已停用'
};

function MerchantWorkspace() {
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: queryKeys.merchantCenter, queryFn: fetchMerchantCenter });
  const latest = query.data?.onboardings[0];
  const merchant = query.data?.merchants[0];
  const editable = !latest || ['REJECTED', 'SUPPLEMENT'].includes(latest.applyStatus);
  const [shopName, setShopName] = useState('');
  const [contactName, setContactName] = useState('');
  const [contactMobile, setContactMobile] = useState('');
  const [address, setAddress] = useState('');
  const [remark, setRemark] = useState('');
  const [latitude, setLatitude] = useState<number | null>(null);
  const [longitude, setLongitude] = useState<number | null>(null);
  const [shopImage, setShopImage] = useState('');

  const locationText = useMemo(
    () => latitude == null || longitude == null ? '尚未定位' : `${latitude.toFixed(5)}, ${longitude.toFixed(5)}`,
    [latitude, longitude]
  );

  const uploadMutation = useMutation({
    mutationFn: uploadMerchantImage,
    onSuccess: (objectKey) => {
      setShopImage(objectKey);
      Toast.show({ content: '店铺照片已上传' });
    }
  });

  const submitMutation = useMutation({
    mutationFn: async (input: MerchantOnboardingInput) => {
      if (latest && ['REJECTED', 'SUPPLEMENT'].includes(latest.applyStatus)) {
        await resubmitMerchantOnboarding(latest.id, { ...input, version: latest.version });
      } else {
        await submitMerchantOnboarding(input);
      }
    },
    onSuccess: () => {
      Toast.show({ content: '申请已提交，两端状态会保持一致' });
      void queryClient.invalidateQueries({ queryKey: queryKeys.merchantCenter });
    }
  });

  const initializeMutation = useMutation({
    mutationFn: initializeMerchant,
    onSuccess: () => {
      Toast.show({ content: '商户收款码已开通' });
      void queryClient.invalidateQueries({ queryKey: queryKeys.merchantCenter });
      void queryClient.invalidateQueries({ queryKey: queryKeys.businessCollectionCode });
    }
  });

  function locate(): void {
    if (!navigator.geolocation) {
      Toast.show({ content: '当前浏览器不支持定位，请使用手机浏览器' });
      return;
    }
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setLatitude(coords.latitude);
        setLongitude(coords.longitude);
      },
      () => Toast.show({ content: '无法获取位置，请允许定位权限后重试' }),
      { enableHighAccuracy: true, timeout: 10_000 }
    );
  }

  function submit(): void {
    if (!shopName.trim() || !contactName.trim() || !/^1[3-9]\d{9}$/.test(contactMobile)) {
      Toast.show({ content: '请完整填写经营名称、联系人和手机号' });
      return;
    }
    if (!address.trim() || latitude == null || longitude == null) {
      Toast.show({ content: '请填写经营地址并获取当前位置' });
      return;
    }
    if (!shopImage) {
      Toast.show({ content: '请上传一张店铺照片' });
      return;
    }
    submitMutation.mutate({
      shopName: shopName.trim(),
      contactName: contactName.trim(),
      contactMobile,
      address: address.trim(),
      latitude,
      longitude,
      shopImages: shopImage,
      remark: remark.trim()
    });
  }

  return (
    <AppShell title="我的商户" subtitle="个人账户与商户共用同一个 MiniPay 钱包" backTo={ROUTES.me} showTabBar>
      <div className={styles.page}>
        <section className={styles.hero}>
          <span className={styles.eyebrow}>MINIPAY BUSINESS</span>
          <h2>{merchant?.name ?? '把个人账户升级为商户'}</h2>
          <p>申请资料、审核状态和商户身份在 H5 与商户平台实时共用，不会重复开户。</p>
          <div className={styles.walletPill}>一套身份 · 一个钱包 · 两种收款码</div>
        </section>

        <AsyncState loading={query.isLoading} error={query.isError ? query.error : undefined} onRetry={() => void query.refetch()}>
          {latest ? (
            <Card title="入驻进度">
              <div className={styles.statusRow}>
                <div><strong>{latest.shopName}</strong><span>{formatDateTime(latest.updatedAt)}</span></div>
                <em data-status={latest.applyStatus}>{STATUS[latest.applyStatus] ?? latest.applyStatus}</em>
              </div>
              {latest.rejectReason ? <InlineNotice tone="warning">审核说明：{latest.rejectReason}</InlineNotice> : null}
              <p className={styles.syncHint}>这就是商户 B 端看到的同一条申请记录，无需再次申请。</p>
            </Card>
          ) : null}

          {merchant ? (
            <Card title="商户能力">
              <div className={styles.merchantRow}>
                <div><strong>{merchant.name}</strong><span>商户号 {merchant.merchantNo}</span></div>
                <em data-status={merchant.status}>{STATUS[merchant.status] ?? merchant.status}</em>
              </div>
              {!merchant.initialized ? (
                <Button block color="primary" loading={initializeMutation.isPending} onClick={() => initializeMutation.mutate(merchant.merchantId)}>
                  开通商户收款码
                </Button>
              ) : (
                <InlineNotice>商户收款能力已开通，可在“我的收款码”切换个人码和商户码。</InlineNotice>
              )}
              {initializeMutation.isError ? <ProblemNotice error={initializeMutation.error} /> : null}
            </Card>
          ) : null}

          {editable ? (
            <Card title={latest ? '补充申请资料' : '申请成为商户'}>
              <div className={styles.form}>
                <label>经营名称<Input value={shopName} maxLength={64} placeholder="例如：米粒便利店" onChange={setShopName} /></label>
                <label>联系人<Input value={contactName} maxLength={64} placeholder="请输入真实联系人" onChange={setContactName} /></label>
                <label>联系电话<Input value={contactMobile} maxLength={11} inputMode="numeric" placeholder="用于商户归属与登录" onChange={setContactMobile} /></label>
                <label>经营地址<Input value={address} maxLength={200} placeholder="请输入详细经营地址" onChange={setAddress} /></label>
                <div className={styles.locationRow}><span>{locationText}</span><Button size="small" fill="outline" onClick={locate}>获取当前位置</Button></div>
                <label className={styles.upload}>
                  店铺照片
                  <input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => {
                    const file = event.target.files?.[0];
                    if (file) uploadMutation.mutate(file);
                  }} />
                  <span>{uploadMutation.isPending ? '正在上传…' : shopImage ? '已上传，可重新选择' : '选择 JPG / PNG / WebP'}</span>
                </label>
                {uploadMutation.isError ? <ProblemNotice error={uploadMutation.error} /> : null}
                <label>申请说明（选填）<TextArea value={remark} maxLength={500} rows={3} placeholder="补充经营情况" onChange={setRemark} /></label>
                {submitMutation.isError ? <ProblemNotice error={submitMutation.error} /> : null}
                <Button block color="primary" size="large" loading={submitMutation.isPending} onClick={submit}>
                  {latest ? '重新提交审核' : '提交商户申请'}
                </Button>
              </div>
            </Card>
          ) : null}

          <a className={styles.portalLink} href="https://merchant.su46proj.site/merchant/" target="_blank" rel="noreferrer">
            打开商户工作台 <span>订单、退款与经营数据 →</span>
          </a>
        </AsyncState>
      </div>
    </AppShell>
  );
}

export default function MerchantPage() {
  return <AuthGate><MerchantWorkspace /></AuthGate>;
}
