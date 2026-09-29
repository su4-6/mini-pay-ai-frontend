import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Input, TextArea, Toast } from 'antd-mobile';
import { useEffect, useMemo, useState } from 'react';
import { AppShell } from '../components/AppShell';
import { AsyncState } from '../components/AsyncState';
import { AuthGate } from '../components/AuthGate';
import { Card } from '../components/Card';
import { InlineNotice } from '../components/InlineNotice';
import { ProblemNotice } from '../components/ProblemNotice';
import { ROUTES } from '../constants/routes';
import { useSession } from '../hooks/useSession';
import { queryKeys } from '../query/keys';
import { fetchMerchantCenter, initializeMerchant, resubmitMerchantOnboarding, submitMerchantOnboarding, uploadMerchantImage } from '../services/merchant';
import type { MerchantOnboardingInput } from '../services/merchant';
import { formatDateTime } from '../utils/datetime';
import { reverseGeocode } from '../utils/amap-loader';
import styles from './merchant.module.less';

const STATUS: Record<string, string> = { PENDING: '审核中', APPROVED: '已通过', REJECTED: '未通过', SUPPLEMENT: '待补充', ACTIVE: '正常', FROZEN: '已冻结', DISABLED: '已停用' };
const MERCHANT_TYPES = [['PERSONAL', '个人商户', '个人经营或小型摊位'], ['INDIVIDUAL', '个体工商户', '持个体工商户资质'], ['ENTERPRISE', '企业商户', '企业主体经营']] as const;

function MerchantWorkspace() {
  const { profile } = useSession();
  const client = useQueryClient();
  const query = useQuery({ queryKey: queryKeys.merchantCenter, queryFn: fetchMerchantCenter });
  const latest = query.data?.onboardings[0];
  const merchants = query.data?.merchants ?? [];
  const editable = !latest || ['REJECTED', 'SUPPLEMENT'].includes(latest.applyStatus);
  const [merchantType, setMerchantType] = useState<MerchantOnboardingInput['merchantType']>('PERSONAL');
  const [shopName, setShopName] = useState('');
  const [mccCode, setMccCode] = useState('');
  const [contactName, setContactName] = useState('');
  const contactMobile = profile?.phone ?? '';
  const [contactEmail, setContactEmail] = useState('');
  const [address, setAddress] = useState('');
  const [remark, setRemark] = useState('');
  const [latitude, setLatitude] = useState<number | null>(null);
  const [longitude, setLongitude] = useState<number | null>(null);
  const [shopImages, setShopImages] = useState<string[]>([]);
  const [locating, setLocating] = useState(false);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    if (!latest || !editable) return;
    setMerchantType(latest.merchantType); setShopName(latest.shopName); setMccCode(latest.mccCode ?? '');
    setContactName(latest.contactName ?? '');
    setContactEmail(latest.contactEmail ?? ''); setAddress(latest.address ?? '');
    setLatitude(latest.latitude ?? null); setLongitude(latest.longitude ?? null);
    setShopImages((latest.shopImages ?? '').split(',').map((item) => item.trim()).filter(Boolean)); setRemark(latest.remark ?? '');
  }, [editable, latest, profile?.phone]);

  const locationText = useMemo(() => latitude == null || longitude == null ? '尚未定位' : `${latitude.toFixed(5)}, ${longitude.toFixed(5)}`, [latitude, longitude]);
  const upload = useMutation({ mutationFn: uploadMerchantImage, onSuccess: (key) => { setShopImages((items) => [...items, key].slice(0, 5)); Toast.show({ content: '店铺照片已上传' }); } });
  const submitApply = useMutation({
    mutationFn: async (input: MerchantOnboardingInput) => !creating && latest && ['REJECTED', 'SUPPLEMENT'].includes(latest.applyStatus) ? resubmitMerchantOnboarding(latest.id, { ...input, version: latest.version }) : submitMerchantOnboarding(input),
    onSuccess: () => { setCreating(false); Toast.show({ content: '门店申请已提交，C 端与商户平台状态同步' }); void client.invalidateQueries({ queryKey: queryKeys.merchantCenter }); }
  });
  const initialize = useMutation({ mutationFn: initializeMerchant, onSuccess: () => { Toast.show({ content: '商户收款码已开通' }); void client.invalidateQueries({ queryKey: queryKeys.merchantCenter }); void client.invalidateQueries({ queryKey: ['merchant-center', 'collection-code'] }); } });

  function locate(): void {
    if (!navigator.geolocation) { Toast.show({ content: '当前浏览器不支持定位' }); return; }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(({ coords }) => {
      setLatitude(coords.latitude);
      setLongitude(coords.longitude);
      void reverseGeocode(coords.longitude, coords.latitude)
        .then((formattedAddress) => {
          setAddress(formattedAddress);
          Toast.show({ content: '已定位并自动填入经营地址' });
        })
        .catch(() => Toast.show({ content: '已获取位置，地址解析失败，请手动补充详细地址' }))
        .finally(() => setLocating(false));
    }, (error) => {
      setLocating(false);
      const message = error.code === error.PERMISSION_DENIED ? '定位权限被拒绝，请在浏览器设置中允许后重试' : '定位失败，请移动到开阔位置后重试';
      Toast.show({ content: message });
    }, { enableHighAccuracy: true, timeout: 10_000, maximumAge: 30_000 });
  }
  function submit(): void {
    if (!shopName.trim() || !contactName.trim()) { Toast.show({ content: '请完整填写经营名称和联系人' }); return; }
    if (contactEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(contactEmail)) { Toast.show({ content: '联系邮箱格式不正确' }); return; }
    if (!address.trim() || latitude == null || longitude == null) { Toast.show({ content: '请填写经营地址并获取当前位置' }); return; }
    if (!shopImages.length) { Toast.show({ content: '请上传 1 至 5 张店铺照片' }); return; }
    submitApply.mutate({ merchantType, shopName: shopName.trim(), mccCode: mccCode || undefined, contactName: contactName.trim(), contactMobile: undefined, contactEmail: contactEmail.trim() || undefined, address: address.trim(), latitude, longitude, shopImages: shopImages.join(','), remark: remark.trim() });
  }

  return <AppShell title="我的商户" subtitle="个人账户与商户共用同一个 MiniPay 钱包" backTo={ROUTES.me} showTabBar><div className={styles.page}>
    <section className={styles.hero}><span className={styles.eyebrow}>MINIPAY BUSINESS</span><h2>{merchants[0]?.name ?? '开设你的线下门店'}</h2><p>同一账户可开设多家门店，每家门店独立审核、独立商户号和收款码。</p><div className={styles.walletPill}>一套身份 · 一个钱包 · 多家门店</div></section>
    <AsyncState loading={query.isLoading} error={query.isError ? query.error : undefined} onRetry={() => void query.refetch()}>
      {query.data?.onboardings.map((application) => <Card key={application.id} title="入驻进度"><div className={styles.statusRow}><div><strong>{application.shopName}</strong><span>{formatDateTime(application.updatedAt)}</span></div><em data-status={application.applyStatus}>{STATUS[application.applyStatus] ?? application.applyStatus}</em></div>{application.rejectReason ? <InlineNotice tone="warning">审核说明：{application.rejectReason}</InlineNotice> : null}<p className={styles.syncHint}>这是商户平台中的同一条门店申请记录。</p></Card>)}
      {merchants.map((merchant) => <Card key={merchant.merchantId} title="商户能力"><div className={styles.merchantRow}><div><strong>{merchant.name}</strong><span>商户号 {merchant.merchantNo}</span></div><em data-status={merchant.status}>{STATUS[merchant.status] ?? merchant.status}</em></div>{!merchant.initialized ? <Button block color="primary" loading={initialize.isPending} onClick={() => initialize.mutate(merchant.merchantId)}>开通该门店收款码</Button> : <InlineNotice>该门店收款码已开通，可在“我的收款码”选择门店。</InlineNotice>}{initialize.isError ? <ProblemNotice error={initialize.error} /> : null}</Card>)}
      {(merchants.length > 0 || (latest && !editable)) && !creating ? <Button block color="primary" fill="outline" size="large" onClick={() => { setCreating(true); setShopName(''); setMccCode(''); setContactName(''); setContactEmail(''); setAddress(''); setLatitude(null); setLongitude(null); setShopImages([]); setRemark(''); }}>新增门店</Button> : null}
      {(editable || creating) ? <Card title={creating ? '新增门店入驻' : latest ? '补充入驻资料' : '商户入驻申请'}><div className={styles.form}>
        <div className={styles.steps}><span><b>1</b>经营信息</span><i /><span><b>2</b>门店资料</span><i /><span><b>3</b>提交审核</span></div>
        <fieldset className={styles.typeField}><legend>商户类型</legend><div className={styles.typeGrid}>{MERCHANT_TYPES.map(([value, title, hint]) => <button key={value} type="button" data-active={merchantType === value} onClick={() => setMerchantType(value)}><strong>{title}</strong><small>{hint}</small></button>)}</div></fieldset>
        <label>经营名称<Input value={shopName} maxLength={64} placeholder="例如：米粒便利店" onChange={setShopName} /></label>
        <label>经营类目<select value={mccCode} onChange={(event) => setMccCode(event.target.value)}><option value="">请选择经营类目</option><option value="5812">餐饮服务</option><option value="5411">商超便利</option><option value="5999">综合零售</option><option value="7299">生活服务</option></select></label>
        <label>联系人<Input value={contactName} maxLength={64} placeholder="请输入真实联系人" onChange={setContactName} /></label>
        <label>联系电话<Input value={contactMobile} readOnly disabled /><small>与当前 MiniPay 登录手机号一致，为保障商户归属不可在入驻页修改</small></label>
        <label>联系邮箱（选填）<Input value={contactEmail} maxLength={128} type="email" placeholder="用于接收入驻通知" onChange={setContactEmail} /></label>
        <label>经营地址<Input value={address} maxLength={200} placeholder="点击下方定位自动填入，也可手动修改" onChange={setAddress} /></label>
        <div className={styles.locationRow}><div><strong>{latitude == null ? '定位经营位置' : '当前位置已获取'}</strong><span>{locationText}</span></div><Button size="small" color="primary" fill="outline" loading={locating} disabled={locating} onClick={locate}>{latitude == null ? '定位并填入地址' : '重新定位'}</Button></div>
        <div className={styles.upload}><div className={styles.fieldTitle}>店铺照片 <span>{shopImages.length}/5</span></div><div className={styles.imageGrid}>{shopImages.map((image, index) => <div className={styles.imageItem} key={image}><span>门店照片 {index + 1}</span><button type="button" onClick={() => setShopImages((items) => items.filter((item) => item !== image))}>移除</button></div>)}{shopImages.length < 5 ? <label className={styles.uploadButton}><b>{upload.isPending ? '…' : '+'}</b><span>{upload.isPending ? '上传中' : '上传照片'}</span><input type="file" accept="image/jpeg,image/png,image/webp" disabled={upload.isPending} onChange={(event) => { const file = event.target.files?.[0]; if (file) upload.mutate(file); event.target.value = ''; }} /></label> : null}</div><small>支持 JPG、PNG、WebP；单张不超过 5MB，最多上传 5 张</small></div>
        {upload.isError ? <ProblemNotice error={upload.error} /> : null}<label>申请说明（选填）<TextArea value={remark} maxLength={500} rows={3} placeholder="补充经营情况" onChange={setRemark} /></label>{submitApply.isError ? <ProblemNotice error={submitApply.error} /> : null}<Button block color="primary" size="large" loading={submitApply.isPending} onClick={submit}>{!creating && latest ? '补充资料并重新提交' : '提交门店入驻申请'}</Button>{creating ? <Button block fill="none" onClick={() => setCreating(false)}>取消新增</Button> : null}
      </div></Card> : null}
      <a className={styles.portalLink} href="https://merchant.su46proj.site/merchant/" target="_blank" rel="noreferrer">打开商户工作台 <span>订单、退款与经营数据 →</span></a>
    </AsyncState>
  </div></AppShell>;
}

export default function MerchantPage() { return <AuthGate><MerchantWorkspace /></AuthGate>; }
