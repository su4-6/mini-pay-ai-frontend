import { lazy, Suspense, useEffect, useState, type ComponentProps } from 'react';
import { Link, useLocation, useNavigate } from '@umijs/max';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Card, Descriptions, Drawer, Dropdown, Empty, Form, Input, Modal, Popconfirm, Progress, Select, Space, Spin, Table, Tag } from 'antd';
import { MenuOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useSidebarPreference } from '@minipay/ui-desktop';
import LocationPicker, { type LocationValue } from '../components/LocationPicker';
import ShopImagesUpload from '../components/ShopImagesUpload';
import { merchantApi, zh, type Merchant, type MerchantApplication, type MerchantApply, type MerchantOrder, type MerchantSession, type WalletBill } from '../services/merchant';
import styles from './index.module.less';

const TrendLineChart = lazy(() => import('../components/TrendLineChart'));
const Line = (props: ComponentProps<typeof TrendLineChart>) => (
  <Suspense fallback={<Spin size="large" />}>
    <TrendLineChart {...props} />
  </Suspense>
);

const money = (cent = 0) => `¥ ${(cent / 100).toFixed(2)}`;
const maskPhone = (phone?: string | null) => phone && phone.length >= 7 ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : (phone ?? '-');
const statusColor = (status?: string) => status === 'ACTIVE' || status === 'ENABLED' || status === 'SUCCEEDED' || status === 'APPROVED' ? 'success' : status === 'PENDING' || status === 'SUPPLEMENT' || status === 'PROCESSING' ? 'processing' : 'error';
const channelOptions = [{ label: 'MiniPay 余额', value: 'WALLET' }, { label: '支付宝沙箱', value: 'ALIPAY' }, { label: '微信沙箱', value: 'WECHAT' }];
const permissionOptions = [{ label: '创建支付', value: 'PAYMENT_CREATE' }, { label: '查询支付', value: 'PAYMENT_QUERY' }, { label: '发起退款', value: 'REFUND_CREATE' }, { label: '查询账单', value: 'BILL_QUERY' }];
const nav = [
  { group: '首页', items: [{ path: '/dashboard', label: '经营看板' }] },
  { group: '商户管理', items: [{ path: '/applications', label: '应用与密钥' }, { path: '/profile', label: '商户设置' }] },
  { group: '订单管理', items: [{ path: '/orders', label: '支付订单' }] },
  { group: '资金管理', items: [{ path: '/wallet', label: '个人钱包' }] },
  { group: '入驻服务', items: [{ path: '/onboarding', label: '商户入驻' }] }
];
const titles: Record<string, [string, string]> = {
  '/dashboard': ['经营看板', '聚合当前商户的支付、退款、渠道和近期订单数据'],
  '/applications': ['应用与密钥', '管理 AppId、回调地址、接口权限、渠道和应用密钥'],
  '/orders': ['支付订单', '查询商户订单、查看详情并发起全额退款'],
  '/wallet': ['个人钱包', '查看当前商家账号共用的个人钱包和资金明细'],
  '/profile': ['商户设置', '维护当前商户资料和商家账号安全'],
  '/onboarding': ['商户入驻', '一个商家账号可申请和经营多个商户']
};

export default function MerchantPortalPage() {
  const location = useLocation(); const navigate = useNavigate(); const queryClient = useQueryClient();
  const {collapsed,setCollapsed}=useSidebarPreference('minipay:merchant:sidebar-collapsed');
  const [mobileMenuOpen,setMobileMenuOpen]=useState(false);
  const [openGroups,setOpenGroups]=useState<Set<string>>(()=>new Set(nav.map(group=>group.group)));
  const [selectedId, setSelectedId] = useState<string | null>(() => localStorage.getItem('minipay-selected-merchant'));
  const session = useQuery({ queryKey: ['merchant-session'], queryFn: merchantApi.session, retry: false });
  const merchants = useQuery({ queryKey: ['merchants'], queryFn: merchantApi.merchants, enabled: session.data?.authenticated === true, retry: false });
  useEffect(() => { if (session.data && !session.data.authenticated) navigate('/login', { replace: true }); }, [session.data?.authenticated]);
  useEffect(() => {
    if (!merchants.data) return;
    const next = merchants.data.some(item => item.merchantId === selectedId) ? selectedId : merchants.data[0]?.merchantId ?? null;
    if (next !== selectedId) setSelectedId(next);
    if (next) localStorage.setItem('minipay-selected-merchant', next);
    if (!next && location.pathname !== '/onboarding') navigate('/onboarding', { replace: true });
  }, [merchants.data, selectedId, location.pathname]);
  if (session.isLoading || (session.data?.authenticated && merchants.isLoading)) return <div className={styles.centered}><Spin size="large" /></div>;
  const selected = merchants.data?.find(item => item.merchantId === selectedId) ?? null;
  const title = titles[location.pathname] ?? titles['/dashboard'];
  const logout = async () => { try { await merchantApi.logout(); } finally { queryClient.clear(); navigate('/login', { replace: true }); } };
  const accountMenu = { items: [{ key: 'profile', label: '商户与账号设置', onClick: () => navigate('/profile') }, { type: 'divider' as const }, { key: 'logout', danger: true, label: '退出登录', onClick: () => void logout() }] };
  const navigation=(mobile=false)=><nav className={styles.nav}>{nav.map(group => <div className={styles.navSection} key={group.group}>{(mobile||!collapsed)&&<button type="button" className={styles.navGroup} aria-expanded={openGroups.has(group.group)} onClick={()=>setOpenGroups(current=>{const next=new Set(current);if(next.has(group.group)){next.delete(group.group);}else{next.add(group.group);}return next;})}><span>{group.group}</span><span>{openGroups.has(group.group)?'−':'+'}</span></button>}{(mobile||collapsed||openGroups.has(group.group))&&group.items.map(item => <Link key={item.path} to={item.path} title={item.label} onClick={()=>setMobileMenuOpen(false)} className={location.pathname === item.path ? styles.active : ''}>{!mobile&&collapsed?<span className={styles.railMark} aria-hidden="true">{item.label.slice(0,1)}</span>:<span>{item.label}</span>}</Link>)}</div>)}</nav>;
  return <div className={`${styles.shell} minipay-desktop-shell`} style={{ '--merchant-sidebar-width': collapsed ? '72px' : '232px' } as React.CSSProperties}>
    <aside className={`${styles.sidebar} minipay-desktop-sidebar`}><div className={styles.brand}><img src={`${MINIPAY_PUBLIC_PATH}minipay-logo.jpg`} alt="" />{!collapsed && <div><strong>minipay</strong><small>商户开放平台</small></div>}</div>{navigation()}{!collapsed && <div className={styles.sideFoot}>MiniPay 商户开放平台</div>}</aside>
    <Drawer className={styles.mobileDrawer} placement="left" width={276} title="商户开放平台导航" open={mobileMenuOpen} onClose={()=>setMobileMenuOpen(false)}><div className={styles.mobileBrand}><img src={`${MINIPAY_PUBLIC_PATH}minipay-logo.jpg`} alt="MiniPay" /><div><strong>minipay</strong><small>商户开放平台</small></div></div>{navigation(true)}</Drawer>
    <div className={styles.workspace}><header className={`${styles.topbar} minipay-desktop-header`}><div className={styles.topStart}><Button className={styles.mobileMenuButton} type="text" aria-label="打开商户端导航" icon={<MenuOutlined />} onClick={()=>setMobileMenuOpen(true)} /><Button className={styles.collapse} type="text" onClick={() => setCollapsed(value => !value)}>{collapsed ? '»' : '«'}</Button><Button type="text" onClick={() => void queryClient.invalidateQueries()}>刷新</Button></div><div className={styles.topEnd}>{selected && <><span className={styles.muted} style={{ fontSize: 12 }} title="登录账号手机号，为名下所有商户/应用的归属身份">账号 {maskPhone(selected.contactMobile)}</span><Select className={styles.merchantPicker} value={selected.merchantId} onChange={value => { setSelectedId(value); localStorage.setItem('minipay-selected-merchant', value); }} options={merchants.data?.map(item => ({ label: `${item.name}（${item.merchantNo}）`, value: item.merchantId }))} /></>}<Dropdown menu={accountMenu} trigger={['click']}><Button type="text"><span className={styles.avatar}>{selected?.name.slice(0, 1) ?? '商'}</span>{!collapsed && <span>{selected?.name ?? '商家账号'}</span>}</Button></Dropdown></div></header>
      <div className={styles.breadcrumb}>商户平台&nbsp;&nbsp;/&nbsp;&nbsp;{title[0]}</div><main className={`${styles.content} minipay-desktop-content`}><div className={styles.pageHeading}><div><h1>{title[0]}</h1><p>{title[1]}</p></div>{selected && <Tag color={statusColor(selected.status)}>{zh.status(selected.status)}</Tag>}</div>{location.pathname === '/onboarding' ? <OnboardingPage onChanged={() => void merchants.refetch()} /> : selected ? <PageContent path={location.pathname} merchant={selected} /> : <Empty description="请先提交商户入驻申请" />}</main>
    </div>
  </div>;
}

function PageContent({ path, merchant }: { path: string; merchant: Merchant }) {
  if (path === '/applications') return <ApplicationsPage merchant={merchant} />;
  if (path === '/orders') return <OrdersPage merchant={merchant} />;
  if (path === '/wallet') return <WalletPage />;
  if (path === '/profile') return <ProfilePage merchant={merchant} />;
  return <DashboardPage merchant={merchant} />;
}

function DashboardPage({ merchant }: { merchant: Merchant }) {
  const { message } = App.useApp(); const queryClient = useQueryClient(); const [days, setDays] = useState<7 | 30>(7);
  const dashboard = useQuery({ queryKey: ['merchant-data', merchant.merchantId, 'dashboard', days], queryFn: () => merchantApi.dashboard(merchant.merchantId, days), enabled: merchant.initialized });
  const orders = useQuery({ queryKey: ['merchant-data', merchant.merchantId, 'orders'], queryFn: () => merchantApi.orders(merchant.merchantId), enabled: merchant.initialized });
  const channels = useQuery({ queryKey: ['merchant-data', merchant.merchantId, 'channels'], queryFn: () => merchantApi.channelDistribution(merchant.merchantId), enabled: merchant.initialized });
  const initialize = useMutation({ mutationFn: () => merchantApi.initialize(merchant.merchantId), onSuccess: async () => { void message.success('商户初始化完成，默认应用与经营收款码已创建'); await queryClient.invalidateQueries(); }, onError: error => void message.error((error as Error).message) });
  if (!merchant.initialized) return <Alert className={styles.initialization} type="info" showIcon message="首次使用需要初始化当前商户" description="系统将幂等创建默认应用和经营收款码。应用密钥请前往“应用与密钥”领取，仅展示一次。" action={<Button type="primary" loading={initialize.isPending} onClick={() => initialize.mutate()}>立即初始化</Button>} />;
  if (dashboard.isLoading) return <Card loading />;
  if (dashboard.error) return <Alert type="error" showIcon message="看板加载失败" description={(dashboard.error as Error).message} action={<Button onClick={() => void dashboard.refetch()}>重试</Button>} />;
  const data = dashboard.data!; const lineData = (data.items ?? []).flatMap(item => [{ date: item.statDate, type: '收款', amount: item.paymentAmountCent / 100 }, { date: item.statDate, type: '退款', amount: item.refundAmountCent / 100 }]);
  const net = data.cumulativePaymentAmountCent - data.cumulativeRefundAmountCent; const totalChannel = channels.data?.reduce((sum, item) => sum + item.amountCent, 0) ?? 0;
  return <><div className={styles.pageHeading}><div /><Space.Compact><Button type={days === 7 ? 'primary' : 'default'} onClick={() => setDays(7)}>近 7 天</Button><Button type={days === 30 ? 'primary' : 'default'} onClick={() => setDays(30)}>近 30 天</Button></Space.Compact></div><div className={styles.metricGrid}><Metric label="今日收款" value={money(data.todayPaymentAmountCent)} meta={`${data.todayPaymentCount} 笔成功交易`} /><Metric label={`${days} 天收款`} value={money(data.paymentAmountCent)} meta={`${data.paymentCount} 笔成功交易`} /><Metric label="累计收款" value={money(data.cumulativePaymentAmountCent)} meta={`累计 ${data.cumulativePaymentCount} 笔`} /><Metric label="累计退款" value={money(data.cumulativeRefundAmountCent)} meta={`累计 ${data.cumulativeRefundCount} 笔`} /></div><div className={styles.dashboardGrid}><Card className={styles.chartCard} title="收退款趋势" extra={`单位：元 · 近 ${days} 天`}><div className={styles.chart}>{lineData.length ? <Line data={lineData} xField="date" yField="amount" colorField="type" axis={{ x: { title: false }, y: { title: false } }} /> : <Empty description="暂无交易数据" />}</div></Card><Card className={styles.healthCard} title="资金与渠道概览"><span className={styles.muted}>累计净收款</span><div className={styles.healthAmount}>{money(net)}</div>{channels.data?.length ? channels.data.map(item => <div className={styles.healthRow} key={`${item.appId}-${item.channel}`}><span>{zh.channel(item.channel)}</span><strong>{totalChannel ? Math.round(item.amountCent / totalChannel * 100) : 0}%</strong></div>) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无渠道数据" />}</Card></div><Card className={styles.tableCard} title="近期订单" style={{ marginTop: 14 }}><Table rowKey="paymentOrderNo" size="middle" pagination={false} dataSource={orders.data?.items.slice(0, 10)} columns={orderColumns()} /></Card></>;
}
function Metric({ label, value, meta }: { label: string; value: string; meta: string }) { return <Card className={styles.metricCard}><span className={styles.metricLabel}>{label}</span><div className={styles.metricValue}>{value}</div><div className={styles.metricMeta}>{meta}</div></Card>; }

function ApplicationsPage({ merchant }: { merchant: Merchant }) {
  const { message, modal } = App.useApp(); const queryClient = useQueryClient(); const [drawer, setDrawer] = useState<MerchantApplication>(); const [applyOpen, setApplyOpen] = useState(false); const [form] = Form.useForm(); const [applyForm] = Form.useForm();
  const applications = useQuery({ queryKey: ['merchant-data', merchant.merchantId, 'applications'], queryFn: () => merchantApi.applications(merchant.merchantId) });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['merchant-data', merchant.merchantId, 'applications'] });
  const reveal = async (app: MerchantApplication) => { try { const result = await merchantApi.viewSecret(merchant.merchantId, app.appId); modal.info({ title: '请立即安全保存应用密钥', width: 620, content: <><Alert type="warning" showIcon message="此密钥只展示一次，关闭后无法再次查看" /><div className={styles.secretBox}>{result.appSecret}</div></> }); await refresh(); } catch (error) { void message.error((error as Error).message); } };
  const reset = async (app: MerchantApplication) => { try { await merchantApi.resetSecret(merchant.merchantId, app.appId, app.version); void message.success('密钥已重置，请立即点击“领取密钥”保存新密钥'); await refresh(); } catch (error) { void message.error((error as Error).message); } };
  return <><div className={styles.pageHeading}><div /><Button type="primary" onClick={() => setApplyOpen(true)}>申请新应用</Button></div><div className={styles.cardList}>{applications.data?.map(app => <Card className={styles.appCard} key={app.appId} title={<div className={styles.appTitle}><strong>{app.appName}</strong>{app.defaultApplication && <Tag color="blue">默认应用</Tag>}<Tag color={statusColor(app.status)}>{zh.status(app.status)}</Tag></div>} extra={<Space wrap><Button onClick={() => { setDrawer(app); form.setFieldsValue({ name: app.appName, notifyUrl: app.notifyUrl, refundNotifyUrl: app.refundNotifyUrl, ipWhiteList: app.ipWhiteList, permissions: app.permissions, availableChannels: app.availableChannels }); }}>配置</Button><Button onClick={() => void reveal(app)}>领取密钥</Button><Popconfirm title="重置后旧密钥立即失效，确认继续？" onConfirm={() => void reset(app)}><Button danger>重置密钥</Button></Popconfirm><Popconfirm title={app.status === 'ACTIVE' ? '确认停用应用？' : '确认启用应用？'} onConfirm={async () => { try { await merchantApi.setApplicationStatus(merchant.merchantId, app.appId, app.version, app.status !== 'ACTIVE'); await refresh(); } catch (error) { void message.error((error as Error).message); } }}><Button>{app.status === 'ACTIVE' ? '停用' : '启用'}</Button></Popconfirm></Space>}><div className={styles.appMeta}><div><span>AppId</span><strong>{app.appId}</strong></div><div><span>支付回调</span><strong>{app.notifyUrl || '尚未配置'}</strong></div><div><span>退款回调</span><strong>{app.refundNotifyUrl || '尚未配置'}</strong></div></div></Card>)}</div>{!applications.isLoading && !applications.data?.length && <Empty description="暂无应用" />}
    <Drawer width={600} title="配置应用接入能力" open={!!drawer} onClose={() => setDrawer(undefined)} destroyOnClose extra={<Button type="primary" onClick={() => form.submit()}>保存</Button>}><Form form={form} layout="vertical" onFinish={async values => { if (!drawer) return; try { await merchantApi.updateApplication(merchant.merchantId, drawer.appId, { ...values, version: drawer.version }); void message.success('应用配置已保存'); setDrawer(undefined); await refresh(); } catch (error) { void message.error((error as Error).message); } }}><Form.Item name="name" label="应用名称" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="notifyUrl" label="支付结果通知地址" rules={[{ required: true }, { type: 'url' }]}><Input placeholder="https://merchant.example.com/callback/payment" /></Form.Item><Form.Item name="refundNotifyUrl" label="退款结果通知地址" rules={[{ type: 'url' }]}><Input placeholder="https://merchant.example.com/callback/refund" /></Form.Item><Form.Item name="ipWhiteList" label="调用 IP 白名单"><Select mode="tags" tokenSeparators={[',']} /></Form.Item><Form.Item name="permissions" label="接口权限" rules={[{ required: true }]}><Select mode="multiple" options={permissionOptions} /></Form.Item><Form.Item name="availableChannels" label="可用支付渠道" rules={[{ required: true }]}><Select mode="multiple" options={channelOptions} /></Form.Item></Form></Drawer>
    <Modal title="申请新应用" open={applyOpen} onCancel={() => setApplyOpen(false)} onOk={() => applyForm.submit()}><Form form={applyForm} layout="vertical" onFinish={async values => { try { await merchantApi.applyForApplication(merchant.merchantId, values.name); void message.success('应用申请已提交，等待运营审核'); setApplyOpen(false); applyForm.resetFields(); } catch (error) { void message.error((error as Error).message); } }}><Form.Item name="name" label="应用名称" rules={[{ required: true }]}><Input placeholder="例如：门店收银系统" /></Form.Item><Alert type="info" showIcon message="审核通过后应用处于停用状态，请先配置回调、领取密钥，再手动启用。" /></Form></Modal></>;
}

function OrdersPage({ merchant }: { merchant: Merchant }) {
  const { message } = App.useApp(); const queryClient = useQueryClient(); const [detail, setDetail] = useState<MerchantOrder>();
  const [page, setPage] = useState(1); const [filters, setFilters] = useState<{ orderNo?: string; status?: string; channel?: string }>({});
  const orders = useQuery({ queryKey: ['merchant-data', merchant.merchantId, 'orders', page, filters], queryFn: () => merchantApi.orders(merchant.merchantId, { page, size: 20, ...filters }) });
  const refund = async (order: MerchantOrder) => { try { await merchantApi.refund(merchant.merchantId, order, '商户后台全额退款'); void message.success('退款申请已成功处理'); setDetail(undefined); await queryClient.invalidateQueries({ queryKey: ['merchant-data', merchant.merchantId] }); } catch (error) { void message.error((error as Error).message); } };
  return <><Card className={styles.tableCard} title={<Form layout="inline" onFinish={values => { setPage(1); setFilters(values); }}><Form.Item name="orderNo"><Input allowClear placeholder="平台/商户订单号" /></Form.Item><Form.Item name="status"><Select allowClear placeholder="订单状态" style={{ width: 130 }} options={['PROCESSING','SUCCEEDED','FAILED','REFUNDED'].map(value => ({ value, label: zh.status(value) }))} /></Form.Item><Form.Item name="channel"><Select allowClear placeholder="支付渠道" style={{ width: 130 }} options={channelOptions} /></Form.Item><Button type="primary" htmlType="submit">查询</Button></Form>}><Table rowKey="paymentOrderNo" loading={orders.isLoading} dataSource={orders.data?.items} pagination={{ current: page, pageSize: 20, total: orders.data?.total, showSizeChanger: false, showTotal: total => `共 ${total} 笔`, onChange: setPage }} columns={[...orderColumns(), { title: '退款状态', dataIndex: 'refundStatus', width: 105, render: (value?: string) => value ? <Tag color={statusColor(value)}>{zh.status(value)}</Tag> : '—' }, { title: '操作', key: 'action', fixed: 'right' as const, render: (_: unknown, row: MerchantOrder) => <Button type="link" onClick={() => setDetail(row)}>查看详情</Button> }]} scroll={{ x: 1180 }} /></Card><Drawer width={520} title="订单详情" open={!!detail} onClose={() => setDetail(undefined)}>{detail && <><Descriptions column={1} bordered size="small" items={[{ key: 'platform', label: '平台订单号', children: detail.paymentOrderNo }, { key: 'merchant', label: '商户订单号', children: detail.merchantOrderNo }, { key: 'app', label: 'AppId', children: detail.appId }, { key: 'subject', label: '交易说明', children: detail.subject }, { key: 'amount', label: '订单金额', children: money(detail.amountCent) }, { key: 'channel', label: '支付渠道', children: zh.channel(detail.channel) }, { key: 'status', label: '支付状态', children: zh.status(detail.status) }, { key: 'refundNo', label: '退款单号', children: detail.refundNo || '—' }, { key: 'refundAmount', label: '退款金额', children: detail.refundAmountCent == null ? '—' : money(detail.refundAmountCent) }, { key: 'refundStatus', label: '退款状态', children: detail.refundStatus ? zh.status(detail.refundStatus) : '—' }, { key: 'refundReason', label: '退款原因', children: detail.refundReason || '—' }, { key: 'created', label: '创建时间', children: dayjs(detail.createdAt).format('YYYY-MM-DD HH:mm:ss') }]} />{detail.status === 'SUCCEEDED' && !detail.refundNo && <Popconfirm title={`确认全额退款 ${money(detail.amountCent)}？`} description="退款将从店主个人钱包扣回付款人，余额不足时整笔失败。" onConfirm={() => void refund(detail)}><Button danger type="primary" style={{ marginTop: 20 }}>发起全额退款</Button></Popconfirm>}</>}</Drawer></>;
}
function orderColumns() { return [{ title: '平台订单号', dataIndex: 'paymentOrderNo', width: 190, ellipsis: true }, { title: '商户订单号', dataIndex: 'merchantOrderNo', width: 190, ellipsis: true }, { title: '交易说明', dataIndex: 'subject', ellipsis: true }, { title: '金额', dataIndex: 'amountCent', width: 110, render: (value: number) => money(value) }, { title: '渠道', dataIndex: 'channel', width: 120, render: (value: string) => zh.channel(value) }, { title: '状态', dataIndex: 'status', width: 100, render: (value: string) => <Tag color={statusColor(value)}>{zh.status(value)}</Tag> }, { title: '创建时间', dataIndex: 'createdAt', width: 165, render: (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm') }]; }

function WalletPage() {
  const wallet = useQuery({ queryKey: ['merchant-data', 'wallet'], queryFn: merchantApi.wallet }); const bills = useQuery({ queryKey: ['merchant-data', 'wallet-bills'], queryFn: merchantApi.walletBills });
  if (wallet.isLoading) return <Card loading />; if (wallet.error) return <Alert type="error" showIcon message="钱包加载失败" description={(wallet.error as Error).message} action={<Button onClick={() => void wallet.refetch()}>重试</Button>} />;
  const data = wallet.data!; const percent = data.annualOutflowLimitCent ? Math.round(data.annualOutflowUsedCent / data.annualOutflowLimitCent * 100) : 0;
  return <><div className={styles.walletGrid}><Card className={styles.walletHero} title="个人钱包总览"><span>可用余额</span><div className={styles.walletBalance}>{money(data.availableAmountCent)}</div><div className={styles.walletFacts}><div><span>冻结金额</span><strong>{money(data.frozenAmountCent)}</strong></div><div><span>总资产</span><strong>{money(data.totalAmountCent)}</strong></div></div></Card><Card title={`${data.annualOutflowYear} 年支付额度`}><Progress percent={percent} strokeColor="#1677ff" /><Descriptions column={1} size="small" items={[{ key: 'used', label: '已用额度', children: money(data.annualOutflowUsedCent) }, { key: 'remain', label: '剩余额度', children: money(data.annualOutflowRemainingCent) }, { key: 'status', label: '钱包状态', children: zh.status(data.status) }]} /></Card></div><Card className={styles.tableCard} title="资金明细"><Table rowKey="billId" loading={bills.isLoading} dataSource={bills.data?.items} pagination={{ pageSize: 15 }} columns={[{ title: '时间', dataIndex: 'occurredAt', render: value => dayjs(value).format('YYYY-MM-DD HH:mm:ss') }, { title: '业务类型', dataIndex: 'businessType', render: zh.businessType }, { title: '交易说明', dataIndex: 'remark' }, { title: '交易对方', dataIndex: 'counterpartyDisplay' }, { title: '金额', render: (_: unknown, row: WalletBill) => <strong style={{ color: row.direction === 'INCOME' ? '#12a150' : '#344054' }}>{row.direction === 'INCOME' ? '+' : '-'}{money(row.amountCent)}</strong> }, { title: '状态', dataIndex: 'status', render: value => <Tag color={statusColor(value)}>{zh.status(value)}</Tag> }]} /></Card></>;
}

function ProfilePage({ merchant }: { merchant: Merchant }) {
  const { message } = App.useApp(); const queryClient = useQueryClient(); const [profileForm] = Form.useForm(); const [passwordForm] = Form.useForm();
  const session = useQuery({ queryKey: ['merchant-session'], queryFn: merchantApi.session, retry: false });
  const passwordConfigured = session.data?.passwordConfigured === true;
  // 运营方创建/代填的资料自动回填，商户只需补充缺失字段后保存即可进入。
  useEffect(() => profileForm.setFieldsValue({
    shortName: merchant.shortName,
    mccCode: merchant.category,
    contactName: merchant.contactName,
    contactMobile: merchant.contactMobile,
    contactEmail: merchant.contactEmail ?? undefined,
    address: merchant.address ?? undefined,
    location: merchant.latitude != null && merchant.longitude != null
      ? { latitude: merchant.latitude, longitude: merchant.longitude }
      : undefined,
    shopImages: merchant.shopImages ?? undefined,
    remark: merchant.remark ?? undefined
  }), [merchant.merchantId, merchant.version]);
  return <div className={styles.profileGrid}><Card title="商户资料"><Descriptions column={1} size="small" style={{ marginBottom: 20 }} items={[{ key: 'no', label: '商户号', children: merchant.merchantNo }, { key: 'name', label: '经营名称', children: merchant.name }, { key: 'status', label: '商户状态', children: <Tag color={statusColor(merchant.status)}>{zh.status(merchant.status)}</Tag> }]} /><Form form={profileForm} layout="vertical" onFinish={async values => {
        try {
          // 地图选点的 location 对象拆成顶层 latitude/longitude 字段
          const { location, ...rest } = values as { location?: LocationValue };
          await merchantApi.updateProfile(merchant.merchantId, {
            ...rest,
            latitude: location?.latitude,
            longitude: location?.longitude,
            version: merchant.version
          });
          void message.success('商户资料已保存');
          await queryClient.invalidateQueries({ queryKey: ['merchants'] });
        } catch (error) { void message.error((error as Error).message); }
      }}>
        {merchant.profileConfirmationRequired && <Alert type="info" showIcon style={{ marginBottom: 16 }} message="运营方已为你创建商户并预填以下资料，如有缺失请补充后保存。" />}
        <Form.Item name="shortName" label="商户简称" rules={[{ required: true }, { min: 2, max: 32, message: '商户简称长度为 2～32 个字符' }]}><Input maxLength={32} placeholder="请输入对外展示简称" /></Form.Item>
        <Form.Item name="mccCode" label="经营类目" rules={[{ required: true }]}><Select options={['餐饮', '零售', '生活服务', '其他'].map(value => ({ label: value, value }))} /></Form.Item>
        <Form.Item name="contactName" label="联系人" rules={[{ required: true }, { min: 2, max: 64, message: '联系人姓名长度为 2～64 个字符' }]}><Input maxLength={64} /></Form.Item>
        <Form.Item name="contactMobile" label="联系电话" extra="为登录账号手机号，不可修改">
          <Input disabled maxLength={11} />
        </Form.Item>
        <Form.Item name="contactEmail" label="联系邮箱" rules={[{ type: 'email', message: '请输入正确的邮箱地址' }]}><Input maxLength={254} /></Form.Item>
        <Form.Item name="address" label="经营地址" rules={[{ max: 200, message: '经营地址不能超过 200 个字符' }]}><Input maxLength={200} placeholder="可在地图上选点自动填充" /></Form.Item>
        <Form.Item name="location" label="经营位置（地图选点）">
          <LocationPicker onAddressChange={(address) => profileForm.setFieldValue('address', address)} />
        </Form.Item>
        <Form.Item name="shopImages" label="店铺图片" valuePropName="value"><ShopImagesUpload /></Form.Item>
        <Form.Item name="remark" label="备注" rules={[{ max: 500, message: '备注不能超过 500 个字符' }]}><Input.TextArea rows={3} maxLength={500} showCount /></Form.Item>
        <Button type="primary" htmlType="submit">保存商户资料</Button>
      </Form></Card><Card title="账号安全">
        <Alert
          type="info"
          showIcon
          message={passwordConfigured ? '登录密码属于商家账号' : '首次登录，请设置登录密码'}
          description={passwordConfigured
            ? '同一商家账号名下的多个商户共用一次登录密码；修改后不会影响运营端会话。'
            : '当前商家账号通过手机验证码首次登录，尚未设置登录密码。设置后可使用手机号和密码登录。'}
          style={{ marginBottom: 20 }}
        />
        <Form form={passwordForm} layout="vertical" onFinish={async values => {
          try {
            if (passwordConfigured) await merchantApi.changePassword(values.currentPassword, values.newPassword);
            else await merchantApi.setPassword(values.newPassword);
            queryClient.setQueryData(['merchant-session'], (current: MerchantSession | undefined) =>
              current ? { ...current, passwordConfigured: true } : current);
            void message.success(passwordConfigured ? '登录密码修改成功' : '登录密码设置成功');
            passwordForm.resetFields();
          } catch (error) { void message.error((error as Error).message); }
        }}>
          {passwordConfigured && <Form.Item name="currentPassword" label="当前密码" rules={[{ required: true }]}><Input.Password /></Form.Item>}
          <Form.Item name="newPassword" label={passwordConfigured ? '新密码' : '登录密码'} rules={[{ required: true }, { pattern: /^(?=.*[A-Za-z])(?=.*\d)[\x21-\x7E]{12,20}$/, message: '密码需为 12-20 位，且同时包含字母和数字' }]}><Input.Password placeholder="12-20 位，需同时包含字母和数字" /></Form.Item>
          <Form.Item name="confirmPassword" label={passwordConfigured ? '确认新密码' : '确认登录密码'} dependencies={['newPassword']} rules={[{ required: true }, ({ getFieldValue }) => ({ validator(_, value) { return !value || value === getFieldValue('newPassword') ? Promise.resolve() : Promise.reject(new Error('两次密码输入不一致')); } })]}><Input.Password /></Form.Item>
          <Button htmlType="submit">{passwordConfigured ? '修改登录密码' : '设置登录密码'}</Button>
        </Form>
      </Card></div>;
}

function OnboardingPage({ onChanged }: { onChanged: () => void }) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const [editing, setEditing] = useState<MerchantApply>();
  const applies = useQuery({ queryKey: ['merchant-data', 'onboardings'], queryFn: merchantApi.onboardings });
  const session = useQuery({ queryKey: ['merchant-session'], queryFn: merchantApi.session, retry: false });
  const loginPhone = session.data?.phone;
  // 联系电话锁定为登录账号手机号，入驻后按此手机号归属商户
  useEffect(() => { if (loginPhone) form.setFieldValue('contactMobile', loginPhone); }, [loginPhone, form]);
  const edit = (item: MerchantApply) => {
    setEditing(item);
    form.setFieldsValue({
      merchantType: item.merchantType, shopName: item.shopName, mccCode: item.mccCode,
      address: item.address,
      location: item.latitude != null && item.longitude != null
        ? { latitude: item.latitude, longitude: item.longitude }
        : undefined,
      shopImages: item.shopImages ?? undefined,
      contactName: item.contactName, contactMobile: loginPhone ?? item.contactMobile,
      contactEmail: item.contactEmail, remark: item.remark
    });
  };
  const clear = () => { setEditing(undefined); form.resetFields(); form.setFieldValue('merchantType', 'PERSONAL'); if (loginPhone) form.setFieldValue('contactMobile', loginPhone); };
  return <div className={styles.profileGrid}>
    <Card title={editing ? `补充并重提：${editing.shopName}` : '提交新的商户入驻'} extra={editing && <Button type="link" onClick={clear}>取消重提</Button>}>
      {editing?.rejectReason && <Alert type="warning" showIcon message="请按审核意见补充资料" description={editing.rejectReason} style={{ marginBottom: 16 }} />}
      <Form form={form} layout="vertical" initialValues={{ merchantType: 'PERSONAL' }} onFinish={async values => {
        try {
          // 地图选点的 location 对象拆成顶层 latitude/longitude 字段
          const { location, ...rest } = values as { location?: LocationValue };
          const body = {
            ...rest,
            latitude: location?.latitude,
            longitude: location?.longitude
          };
          if (editing) await merchantApi.resubmitOnboarding(editing.id, { ...body, version: editing.version });
          else await merchantApi.submitOnboarding(body);
          void message.success(editing ? '资料已补充并重新提交' : '入驻申请已提交，等待运营审核');
          clear();
          await queryClient.invalidateQueries({ queryKey: ['merchant-data', 'onboardings'] });
          onChanged();
        } catch (error) { void message.error((error as Error).message); }
      }}>
        <Form.Item name="merchantType" label="商户类型" rules={[{ required: true }]}><Select options={[{ label: '个人商户', value: 'PERSONAL' }, { label: '个体工商户', value: 'INDIVIDUAL' }, { label: '企业商户', value: 'ENTERPRISE' }]} /></Form.Item>
        <Form.Item name="shopName" label="经营名称" rules={[{ required: true }, { min: 2, max: 64 }]}><Input placeholder="例如：星河咖啡店" /></Form.Item>
        <Form.Item name="mccCode" label="经营类目代码"><Input placeholder="例如：5812" /></Form.Item>
        <Form.Item name="address" label="经营地址" rules={[{ max: 200, message: '经营地址不能超过 200 个字符' }]}>
          <Input maxLength={200} placeholder="选填，可在地图上选点自动填充" />
        </Form.Item>
        <Form.Item name="location" label="经营位置（地图选点）" rules={[{ required: true, message: '请选择经营位置' }]}>
          <LocationPicker onAddressChange={(address) => form.setFieldValue('address', address)} />
        </Form.Item>
        <Form.Item name="shopImages" label="店铺图片" valuePropName="value" rules={[{ required: true, message: '请上传 1 至 5 张店铺照片' }]}>
          <ShopImagesUpload />
        </Form.Item>
        <Form.Item name="contactName" label="联系人" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="contactMobile" label="联系电话" extra="为登录账号手机号，不可修改；商户将归属到该手机号">
          <Input disabled maxLength={11} />
        </Form.Item>
        <Form.Item name="contactEmail" label="联系邮箱" rules={[{ type: 'email' }]}><Input /></Form.Item>
        <Form.Item name="remark" label="申请说明"><Input.TextArea rows={3} /></Form.Item>
        <Button type="primary" htmlType="submit">{editing ? '重新提交审核' : '提交入驻申请'}</Button>
      </Form>
    </Card>
    <Card title="申请记录">{applies.data?.items.length ? <div className={styles.cardList}>{applies.data.items.map((item: MerchantApply) => <Card size="small" key={item.id} title={item.shopName} extra={<Space><Tag color={statusColor(item.applyStatus)}>{zh.status(item.applyStatus)}</Tag>{['REJECTED', 'SUPPLEMENT'].includes(item.applyStatus) && <Button size="small" onClick={() => edit(item)}>补充资料</Button>}</Space>}><p>类型：{item.merchantType === 'ENTERPRISE' ? '企业商户' : '个人商户'}</p><p>联系人：{item.contactName} · {item.contactMobile}</p>{item.rejectReason && <Alert type="warning" showIcon message="审核意见" description={item.rejectReason} />}</Card>)}</div> : <Empty description="暂无入驻申请" />}</Card>
  </div>;
}
