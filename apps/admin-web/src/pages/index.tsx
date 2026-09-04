import { useEffect, useState } from 'react';
import { Link, useLocation } from '@umijs/max';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert, App, Button, Card, DatePicker, Descriptions, Drawer, Form, Input, Layout,
  Menu, Modal, Select, Space, Spin, Statistic, Table, Tag
} from 'antd';
import dayjs from 'dayjs';
import {
  DownloadOutlined, EyeOutlined, MenuFoldOutlined, MenuOutlined, MenuUnfoldOutlined
} from '@ant-design/icons';
import {
  adminApi, type Account, type Audit, type LedgerTransaction, type Merchant,
  type PaymentOrder, type RefundOrder, type Role, type TransferOrder, type Wallet,
  type RechargeOrder, type WithdrawalOrder, type FoodOrder, type CollectionRecord
} from '../services/admin';
import { useSidebarPreference } from '@minipay/ui-desktop';
import { submitAdminLogout } from '../services/auth';
import styles from './style.less';

const { Header, Sider, Content } = Layout;
const { RangePicker } = DatePicker;
const money = (v = 0) => `¥ ${(Number(v) / 100).toFixed(2)}`;
const time = (v?: unknown) => v ? dayjs(String(v)).format('YYYY-MM-DD HH:mm:ss') : '-';
const roleName: Record<string, string> = {
  system_super_admin: '超级管理员', system_account_admin: '账号管理员',
  system_auditor: '只读审计员', platform_admin: '运营账号', merchant_owner: '商户所有人'
};
const valueName: Record<string, string> = {
  ACTIVE: '启用', DISABLED: '停用', PENDING: '待处理', FROZEN: '冻结', CLOSED: '已关闭',
  SUCCESS: '成功', SUCCEEDED: '成功', FAILED: '失败', REJECTED: '已拒绝', UP: '正常', DOWN: '异常',
  PROCESSING: '处理中', PAID: '已支付', COMPLETED: '已完成', CANCELLED: '已取消',
  SMS: '短信验证码', PASSWORD: '登录密码', LOGIN_PASSWORD: '登录密码', PAYMENT_PASSWORD: '支付密码',
  CONSUMER: '消费者', MERCHANT: '商户', PLATFORM: '平台', DEBIT: '借方', CREDIT: '贷方',
  WALLET: 'MiniPay 余额', WALLET_BALANCE: 'MiniPay 余额', ALIPAY: '支付宝', WECHAT: '微信支付',
  ACCOUNT_STATUS: '账号状态变更', ACCOUNT_UNLOCK: '账号解锁', CREDENTIAL_RESET: '重置登录凭证',
  PAYMENT_PASSWORD_RESET: '重置支付密码', SESSIONS_REVOKE: '强制退出会话',
  BACKOFFICE_CREATE: '创建后台账号', BACKOFFICE_ROLE_CHANGE: '调整后台角色',
  OWN_PASSWORD_SET: '管理员设置登录密码', ACCOUNT: '账号', MERCHANT_APPLICATION: '商户应用',
  PAYMENT: '支付', REFUND: '退款', TRANSFER: '转账', RECHARGE: '充值', WITHDRAWAL: '提现',
  PAYMENT_ORDER: '支付订单', REFUND_ORDER: '退款订单', TRANSFER_ORDER: '转账订单',
  RECHARGE_ORDER: '充值订单', WITHDRAWAL_ORDER: '提现订单', OPENING_GRANT: '开户赠金',
  WALLET_PAYMENT: '余额支付', INTERNAL_TRANSFER: '站内转账'
};
const displayValue = (value?: unknown) => value === null || value === undefined || value === ''
  ? '-' : valueName[String(value)] ?? String(value);
const fieldName: Record<string, string> = {
  userId: '用户ID', minipayNo: 'MiniPay号', displayName: '名称', maskedMobile: '手机号',
  maskedEmail: '邮箱', roles: '角色', version: '数据版本', createdAt: '创建时间', updatedAt: '更新时间',
  status: '状态', merchantId: '商户ID', merchantNo: '商户号', name: '商户名称', shortName: '简称',
  ownerUserId: '所有者ID', applicationCount: '应用数', paymentOrderNo: '支付订单号',
  merchantOrderNo: '商户订单号', merchantName: '商户名称', appId: '应用ID', amountCent: '金额',
  currency: '币种', channel: '渠道', subject: '商品说明', payerMasked: '付款方', failureCode: '失败码',
  refundNo: '退款单号', reason: '原因', transferNo: '转账单号', receiverMasked: '收款方',
  accountId: '钱包ID', accountNo: '钱包号', ownerType: '所有者类型', ownerId: '所有者ID',
  accountRole: '账户角色', availableAmountCent: '可用余额', frozenAmountCent: '冻结金额',
  transactionId: '流水ID', transactionNo: '流水号', businessType: '业务类型', businessNo: '业务单号',
  debitTotalAmountCent: '借方总额', creditTotalAmountCent: '贷方总额', occurredAt: '发生时间',
  entryId: '分录ID', direction: '方向', action: '操作', targetType: '目标类型', targetId: '目标ID',
  result: '结果', requestId: '请求号', actorUserId: '操作人ID', authenticationMethod: '认证方式'
  ,credentialType: '登录凭证类型', onboardingStatus: '开户状态', loginPasswordSet: '已设置登录密码',
  paymentPasswordSet: '已设置支付密码', rechargeNo: '充值单号', withdrawalNo: '提现单号',
  userMasked: '用户', bankName: '银行', bankCardMasked: '银行卡', bankRequestNo: '银行请求号',
  cardId: '银行卡ID', provider: '通道服务商', cardType: '卡类型', maskedCardNo: '银行卡号',
  holderName: '持卡人', verifiedAt: '认证时间'
};
const statusTag = (v?: string) => <Tag color={['ACTIVE','SUCCESS','SUCCEEDED','UP','PAID','COMPLETED'].includes(v ?? '') ? 'success' : ['FAILED','DISABLED','DOWN','REJECTED'].includes(v ?? '') ? 'error' : 'blue'}>{displayValue(v)}</Tag>;
const isMoney = (key: string) => key.endsWith('AmountCent') || key === 'amountCent';

function Value({ name, value }: { name: string; value: unknown }) {
  if (value === null || value === undefined || value === '') return <>-</>;
  if (Array.isArray(value)) return <Space wrap>{value.map((v) => <Tag key={String(v)}>{roleName[String(v)] ?? String(v)}</Tag>)}</Space>;
  if (typeof value === 'object') return <JsonDetails value={value as Record<string, unknown>} />;
  if (typeof value === 'boolean') return <Tag color={value ? 'success' : 'default'}>{value ? '是' : '否'}</Tag>;
  if (isMoney(name)) return <>{money(Number(value))}</>;
  if (name === 'status' || name === 'result') return statusTag(String(value));
  if (name.endsWith('At')) return <>{time(value)}</>;
  return <>{displayValue(value)}</>;
}

function JsonDetails({ value }: { value?: Record<string, unknown> }) {
  if (!value) return null;
  return <Descriptions bordered size="small" column={1} items={Object.entries(value).map(([key, item]) => ({ key, label: fieldName[key] ?? key, children: <Value name={key} value={item} /> }))} />;
}

export default function AdminPortal() {
  const location = useLocation();
  const [mobileMenuOpen,setMobileMenuOpen]=useState(false);
  const {collapsed,toggleCollapsed}=useSidebarPreference('minipay:admin:sidebar-collapsed');
  const session = useQuery({ queryKey: ['admin-session'], queryFn: adminApi.session, retry: false });
  useEffect(() => { if (session.data && !session.data.authenticated) window.location.assign('/login'); }, [session.data]);
  if (session.isLoading || !session.data?.authenticated) return <div className={styles.center}><Spin size="large" /></div>;
  const roles = session.data.admin?.roles ?? [];
  const routeItem=(key:string,label:string)=>({key,label:<Link to={key}>{label}</Link>,icon:collapsed?<span className={styles.railMark} aria-hidden="true">{label.slice(0,1)}</span>:undefined});
  const items = [
    routeItem('/','系统概览'),
    {key:'business',label:'业务管理',icon:collapsed?<span className={styles.railMark} aria-hidden="true">业</span>:undefined,children:[
      routeItem('/accounts','账号管理'),
      routeItem('/merchants','商户关联'),
      routeItem('/orders','订单中心'),
      routeItem('/wallets','钱包中心')
    ]},
    {key:'system',label:'系统与安全',icon:collapsed?<span className={styles.railMark} aria-hidden="true">安</span>:undefined,children:[
      ...(roles.includes('system_super_admin')?[routeItem('/backoffice','后台账号')]:[]),
      routeItem('/audits','安全审计'),
      routeItem('/security','登录安全'),
      routeItem('/settings','个人设置')
    ]}
  ];
  return <Layout className={`${styles.shell} minipay-desktop-shell`}>
    <Sider className={`${styles.sidebar} minipay-desktop-sidebar`} theme="light" width={232} collapsedWidth={72} collapsed={collapsed} trigger={null}>
      <div className={styles.brand}><img src={`${MINIPAY_PUBLIC_PATH}minipay-logo.svg`} alt="MiniPay" />{!collapsed&&<div><strong>minipay</strong><small>系统管理平台</small></div>}</div>
      <Menu className={styles.menu} selectedKeys={[location.pathname]} defaultOpenKeys={['business','system']} items={items} inlineCollapsed={collapsed} />
      {!collapsed&&<div className={styles.sidebarFooter}>系统安全与全局审计</div>}
    </Sider>
    <Drawer className={styles.mobileDrawer} placement="left" width={276} title="系统管理平台导航" open={mobileMenuOpen} onClose={()=>setMobileMenuOpen(false)}>
      <div className={styles.mobileBrand}><img src={`${MINIPAY_PUBLIC_PATH}minipay-logo.svg`} alt="MiniPay" /><div><strong>minipay</strong><small>系统管理平台</small></div></div>
      <Menu mode="inline" selectedKeys={[location.pathname]} defaultOpenKeys={['business','system']} items={items} onClick={()=>setMobileMenuOpen(false)} />
    </Drawer>
    <Layout className={styles.workspace} style={{marginLeft:collapsed?72:232}}>
      <Header className={`${styles.header} minipay-desktop-header`}><Space><Button className={styles.mobileMenuButton} type="text" aria-label="打开管理端导航" onClick={()=>setMobileMenuOpen(true)} icon={<MenuOutlined />} /><Button className={styles.desktopMenuButton} type="text" aria-label={collapsed?'展开侧栏':'收起侧栏'} onClick={toggleCollapsed} icon={collapsed?<MenuUnfoldOutlined />:<MenuFoldOutlined />} /><span className={styles.headerLabel}>MiniPay 系统安全中心</span></Space><Space>
        {roles.map((r) => <Tag key={r}>{roleName[r] ?? r}</Tag>)}
        <span className={styles.avatar}>{session.data.admin?.displayName?.slice(0, 1) ?? '管'}</span>
        <strong className={styles.displayName}>{session.data.admin?.displayName}</strong><Button type="link" onClick={()=>void submitAdminLogout()}>退出</Button>
      </Space></Header>
      <Content className={`${styles.content} minipay-desktop-content`}>
        {location.pathname === '/' ? <Dashboard roles={roles} /> : location.pathname === '/accounts' ? <Accounts roles={roles} /> :
          location.pathname === '/merchants' ? <Merchants /> : location.pathname === '/orders' ? <Orders /> :
          location.pathname === '/wallets' ? <Wallets /> : location.pathname === '/backoffice' ? <Backoffice /> :
          location.pathname === '/security' ? <SecurityCenter /> : location.pathname === '/settings' ?
          <Settings roles={roles} displayName={session.data.admin?.displayName} /> : <Audits />}
      </Content>
    </Layout>
  </Layout>;
}

function Dashboard({ roles }: { roles: Role[] }) {
  const summary = useQuery({ queryKey: ['dashboard','summary'], queryFn: adminApi.summary });
  const merchants = useQuery({ queryKey: ['dashboard','merchants'], queryFn: () => adminApi.merchants({ page: 0, size: 1 }) });
  const orders = useQuery({ queryKey: ['dashboard','orders'], queryFn: () => adminApi.orders('payments', { page: 0, size: 1 }) });
  const wallets = useQuery({ queryKey: ['dashboard','wallets'], queryFn: () => adminApi.wallets({ page: 1, size: 1 }) });
  const health = useQuery({ queryKey: ['dashboard','health'], queryFn: adminApi.systemHealth, refetchInterval: 30_000 });
  const audits = useQuery({ queryKey: ['dashboard','audits'], queryFn: () => adminApi.audits({ page: 1, size: 5 }) });
  const logins = useQuery({ queryKey: ['dashboard','logins'], queryFn: () => adminApi.loginAudits({ page: 0, size: 5 }) });
  return <><h1>系统概览</h1>
    <Alert className={styles.roleBanner} showIcon type={health.data?.status === 'DEGRADED' ? 'warning' : roles.includes('system_auditor') ? 'info' : 'success'}
      message={health.data?.status === 'DEGRADED' ? '检测到服务异常，请前往“登录安全”查看' : `当前权限：${roles.map((r) => roleName[r] ?? r).join('、')}`}
      description={roles.includes('system_super_admin') ? '可管理系统管理员、账号、业务数据与全局审计。' : roles.includes('system_account_admin') ? '可执行账号解锁、停用、会话强退与登录方式重置。' : '当前为只读审计权限，可查询业务数据和安全记录，不可执行变更。'} />
    <div className={styles.grid}>{[
      ['消费者',summary.data?.consumers],['商户所有人',summary.data?.merchantOwners],['运营账号',summary.data?.operators],
      ['管理员账号',summary.data?.administrators],['商户总数',merchants.data?.total],['支付订单',orders.data?.total],['钱包账户',wallets.data?.total]
    ].map(([title,value]) => <Card key={String(title)}><Statistic title={title} value={Number(value ?? 0)} /></Card>)}</div>
    <div className={styles.twoColumns}>
      <Card title="最近管理操作"><Table size="small" rowKey="auditId" pagination={false} dataSource={audits.data?.items} columns={[{title:'时间',dataIndex:'occurredAt',render:time},{title:'操作',dataIndex:'action',render:displayValue},{title:'结果',dataIndex:'result',render:statusTag}]} /></Card>
      <Card title="最近管理员登录"><Table size="small" rowKey="auditId" pagination={false} dataSource={logins.data?.items} columns={[{title:'时间',dataIndex:'occurredAt',render:time},{title:'管理员',dataIndex:'displayName',render:(v)=>v||'-'},{title:'结果',dataIndex:'result',render:statusTag}]} /></Card>
    </div>
  </>;
}

type AccountAction = { account: Account; type: 'unlock'|'sessions/revoke'|'credential-reset'|'payment-password-reset'|'status'|'role' };
function Accounts({ roles }: { roles: Role[] }) {
  const { message } = App.useApp(); const client = useQueryClient(); const [form] = Form.useForm();
  const [page,setPage] = useState(1); const [filters,setFilters] = useState<Record<string,unknown>>({});
  const [detail,setDetail] = useState<Account>(); const [action,setAction] = useState<AccountAction>();
  const query = useQuery({ queryKey:['accounts',page,filters], queryFn:()=>adminApi.accounts({page,size:20,...filters}) });
  const cards = useQuery({ queryKey:['account-bank-cards',detail?.userId], queryFn:()=>adminApi.bankCards(detail!.userId), enabled:!!detail });
  const submit = async (values: { reason: string; role?: string }) => {
    if (!action) return;
    try {
      if (action.type === 'status') await adminApi.status(action.account, action.account.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE', values.reason);
      else if (action.type === 'role') await adminApi.role(action.account, values.role!, values.reason);
      else await adminApi.accountAction(action.account, action.type, undefined, values.reason);
      void message.success('操作成功'); setAction(undefined); form.resetFields(); await client.invalidateQueries({queryKey:['accounts']});
    } catch (e) {
      const code=(e as Error).message;
      if (code==='ACCOUNT_VERSION_CONFLICT' || code==='VERSION_CONFLICT') {
        void message.warning('账号信息已被其他管理员更新，请重新加载后再操作');
        setAction(undefined); form.resetFields();
        await client.invalidateQueries({queryKey:['accounts']});
      } else void message.error(code);
    }
  };
  return <><div className={styles.pageTitle}><h1>账号管理</h1><span>统一管理消费者、商户、运营与系统账号</span></div><Card>
    <Form layout="inline" onFinish={(v)=>{setPage(1);setFilters(v);}}><Form.Item name="minipayNo"><Input allowClear placeholder="MiniPay号" /></Form.Item><Form.Item name="mobile"><Input allowClear maxLength={11} placeholder="精确手机号" /></Form.Item><Form.Item name="status"><Select allowClear placeholder="状态" style={{width:130}} options={['ACTIVE','DISABLED'].map(value=>({value,label:displayValue(value)}))} /></Form.Item><Button htmlType="submit" type="primary">查询</Button></Form>
    <Table rowKey="userId" loading={query.isLoading} dataSource={query.data?.items} pagination={{current:page,pageSize:20,total:query.data?.total,onChange:setPage}}
      columns={[{title:'MiniPay号',dataIndex:'minipayNo'},{title:'名称',dataIndex:'displayName'},{title:'手机',dataIndex:'maskedMobile'},{title:'状态',dataIndex:'status',render:statusTag},{title:'登录密码',dataIndex:'loginPasswordSet',render:(v:boolean)=><Tag color={v?'success':'default'}>{v?'已设置':'未设置'}</Tag>},{title:'支付密码',dataIndex:'paymentPasswordSet',render:(v:boolean)=><Tag color={v?'success':'default'}>{v?'已设置':'未设置'}</Tag>},{title:'账号能力',dataIndex:'roles',render:(v:string[])=><Space wrap>{v.map(r=><Tag key={r}>{roleName[r]??r}</Tag>)}</Space>},{title:'操作',render:(_,a:Account)=><Space wrap><Button type="link" icon={<EyeOutlined />} onClick={()=>setDetail(a)}>详情</Button>{roles.includes('system_auditor')?null:<><Button onClick={()=>setAction({account:a,type:'unlock'})}>解锁</Button><Button onClick={()=>setAction({account:a,type:'sessions/revoke'})}>强退</Button><Button onClick={()=>setAction({account:a,type:'credential-reset'})}>重置登录</Button>{a.paymentPasswordSet&&<Button danger onClick={()=>setAction({account:a,type:'payment-password-reset'})}>重置支付密码</Button>}{roles.includes('system_super_admin')&&a.roles.some(r=>['platform_admin','system_super_admin','system_account_admin','system_auditor'].includes(r))?<Button onClick={()=>setAction({account:a,type:'role'})}>调整角色</Button>:null}<Button danger={a.status==='ACTIVE'} onClick={()=>setAction({account:a,type:'status'})}>{a.status==='ACTIVE'?'停用':'启用'}</Button></>}</Space>}]} scroll={{x:1350}} />
  </Card>
  <Drawer width={680} open={!!detail} onClose={()=>setDetail(undefined)} title="账号与银行卡详情">{detail&&<><Descriptions bordered column={1} items={[{key:'no',label:'MiniPay号',children:detail.minipayNo},{key:'name',label:'名称',children:detail.displayName},{key:'mobile',label:'手机号',children:detail.maskedMobile||'-'},{key:'email',label:'邮箱',children:detail.maskedEmail||'-'},{key:'status',label:'状态',children:statusTag(detail.status)},{key:'credential',label:'登录凭证',children:displayValue(detail.credentialType)},{key:'loginPwd',label:'登录密码',children:detail.loginPasswordSet?'已设置':'未设置'},{key:'payPwd',label:'支付密码',children:detail.paymentPasswordSet?'已设置':'未设置'},{key:'onboarding',label:'开户状态',children:displayValue(detail.onboardingStatus)},{key:'roles',label:'角色',children:<Space wrap>{detail.roles.map(r=><Tag key={r}>{roleName[r]??r}</Tag>)}</Space>},{key:'id',label:'用户ID',children:detail.userId},{key:'created',label:'创建时间',children:time(detail.createdAt)}]} /><h3>已绑定银行卡（仅显示脱敏信息）</h3><Table size="small" rowKey="cardId" loading={cards.isLoading} pagination={false} dataSource={cards.data} columns={[{title:'银行',dataIndex:'bankName'},{title:'卡类型',dataIndex:'cardType',render:displayValue},{title:'银行卡号',dataIndex:'maskedCardNo'},{title:'持卡人',dataIndex:'holderName'},{title:'状态',dataIndex:'status',render:statusTag},{title:'认证时间',dataIndex:'verifiedAt',render:time}]} /><Alert className={styles.drawerAlert} showIcon type="warning" message="重置支付密码后，用户必须通过本人短信验证重新设置；系统不会展示或生成明文支付密码。" /></>}</Drawer>
  <Modal title="确认管理操作" open={!!action} onCancel={()=>{setAction(undefined);form.resetFields();}} onOk={()=>form.submit()} destroyOnClose><Form form={form} layout="vertical" onFinish={submit}>{action?.type==='role'&&<Form.Item name="role" label="新角色" rules={[{required:true}]}><Select options={['platform_admin','system_super_admin','system_account_admin','system_auditor'].map(value=>({value,label:roleName[value]}))} /></Form.Item>}<Form.Item name="reason" label="操作原因" rules={[{required:true,message:'请输入可审计的操作原因'},{min:3,max:200,message:'操作原因请输入 3 至 200 个字符'}]}><Input.TextArea rows={3} placeholder="该原因会写入安全审计记录" /></Form.Item></Form></Modal>
  </>;
}

function Merchants() {
  const [page,setPage]=useState(1); const [filters,setFilters]=useState<Record<string,unknown>>({}); const [selected,setSelected]=useState<string>();
  const list=useQuery({queryKey:['merchants',page,filters],queryFn:()=>adminApi.merchants({page:page-1,size:20,...filters})});
  const detail=useQuery({queryKey:['merchant',selected],queryFn:()=>adminApi.merchant(selected!),enabled:!!selected});
  return <><div className={styles.pageTitle}><h1>商户关联</h1><span>查询商户、所有者与应用关联</span></div><Card><Form layout="inline" onFinish={v=>{setPage(1);setFilters(v);}}><Form.Item name="merchantNo"><Input allowClear placeholder="商户号" /></Form.Item><Form.Item name="name"><Input allowClear placeholder="商户名称" /></Form.Item><Form.Item name="status"><Select allowClear placeholder="状态" style={{width:140}} options={['ACTIVE','DISABLED','PENDING'].map(value=>({value,label:displayValue(value)}))} /></Form.Item><Button htmlType="submit" type="primary">查询</Button></Form><Table rowKey="merchantId" loading={list.isLoading} dataSource={list.data?.items} pagination={{current:page,pageSize:20,total:list.data?.total,onChange:setPage}} columns={[{title:'商户号',dataIndex:'merchantNo'},{title:'商户名称',dataIndex:'name'},{title:'简称',dataIndex:'shortName'},{title:'状态',dataIndex:'status',render:statusTag},{title:'应用数',dataIndex:'applicationCount'},{title:'创建时间',dataIndex:'createdAt',render:time},{title:'操作',render:(_,r:Merchant)=><Button type="link" icon={<EyeOutlined />} onClick={()=>setSelected(r.merchantId)}>详情</Button>}]} /></Card><Drawer width={560} title="商户详情" open={!!selected} onClose={()=>setSelected(undefined)} loading={detail.isLoading}><JsonDetails value={detail.data as unknown as Record<string,unknown>} /></Drawer></>;
}

type OrderKind='payments'|'refunds'|'transfers'|'recharges'|'withdrawals'|'food-orders'|'collection-records'; type OrderRow=PaymentOrder|RefundOrder|TransferOrder|RechargeOrder|WithdrawalOrder|FoodOrder|CollectionRecord;
function Orders() {
  const [kind,setKind]=useState<OrderKind>('payments'); const [page,setPage]=useState(1); const [filters,setFilters]=useState<Record<string,unknown>>({}); const [selected,setSelected]=useState<string>();
  const query=useQuery({queryKey:['orders',kind,page,filters],queryFn:()=>adminApi.orders<OrderRow>(kind,{page:page-1,size:20,...filters})});
  const detail=useQuery({queryKey:['order',kind,selected],queryFn:()=>adminApi.orderDetail(kind,selected!),enabled:!!selected});
  const no=(r:OrderRow):string=>String('billId'in r?r.billId:'orderRefId'in r?r.orderRefId:'refundNo'in r?r.refundNo:'transferNo'in r?r.transferNo:'rechargeNo'in r?r.rechargeNo:'withdrawalNo'in r?r.withdrawalNo:r.paymentOrderNo);
  // The column variants intentionally target different members of the order union.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const columns:any[] = kind==='payments'?[{title:'支付订单号',dataIndex:'paymentOrderNo'},{title:'商户',dataIndex:'merchantName'},{title:'商户订单号',dataIndex:'merchantOrderNo'},{title:'金额',dataIndex:'amountCent',render:money},{title:'渠道',dataIndex:'channel',render:displayValue},{title:'状态',dataIndex:'status',render:statusTag},{title:'创建时间',dataIndex:'createdAt',render:time}]:kind==='refunds'?[{title:'退款单号',dataIndex:'refundNo'},{title:'支付订单号',dataIndex:'paymentOrderNo'},{title:'商户',dataIndex:'merchantName'},{title:'金额',dataIndex:'amountCent',render:money},{title:'状态',dataIndex:'status',render:statusTag},{title:'创建时间',dataIndex:'createdAt',render:time}]:kind==='transfers'?[{title:'转账单号',dataIndex:'transferNo'},{title:'付款方',dataIndex:'payerMasked'},{title:'收款方',dataIndex:'receiverMasked'},{title:'金额',dataIndex:'amountCent',render:money},{title:'状态',dataIndex:'status',render:statusTag},{title:'创建时间',dataIndex:'createdAt',render:time}]:kind==='recharges'?[{title:'充值单号',dataIndex:'rechargeNo'},{title:'用户',dataIndex:'userMasked'},{title:'金额',dataIndex:'amountCent',render:money},{title:'渠道',dataIndex:'channel',render:displayValue},{title:'银行',dataIndex:'bankName'},{title:'银行卡',dataIndex:'bankCardMasked'},{title:'状态',dataIndex:'status',render:statusTag},{title:'创建时间',dataIndex:'createdAt',render:time}]:kind==='withdrawals'?[{title:'提现单号',dataIndex:'withdrawalNo'},{title:'用户',dataIndex:'userMasked'},{title:'金额',dataIndex:'amountCent',render:money},{title:'银行',dataIndex:'bankName'},{title:'银行卡',dataIndex:'bankCardMasked'},{title:'状态',dataIndex:'status',render:statusTag},{title:'创建时间',dataIndex:'createdAt',render:time}]:kind==='food-orders'?[{title:'外卖订单号',dataIndex:'externalOrderNo'},{title:'用户ID',dataIndex:'userId'},{title:'金额',dataIndex:'amountCent',render:money},{title:'支付状态',dataIndex:'paymentStatus',render:statusTag},{title:'履约状态',dataIndex:'fulfillmentStatus',render:statusTag},{title:'退款状态',dataIndex:'refundStatus',render:statusTag},{title:'创建时间',dataIndex:'createdAt',render:time}]:[{title:'业务单号',dataIndex:'businessNo'},{title:'所有者ID',dataIndex:'ownerId'},{title:'业务类型',dataIndex:'businessType',render:displayValue},{title:'方向',dataIndex:'direction',render:displayValue},{title:'金额',dataIndex:'amountCent',render:money},{title:'状态',dataIndex:'status',render:statusTag},{title:'发生时间',dataIndex:'occurredAt',render:time}];
  columns.push({title:'操作',render:(_:unknown,r:OrderRow)=><Button type="link" icon={<EyeOutlined />} onClick={()=>setSelected(no(r))}>详情</Button>});
  const labels:Record<OrderKind,string>={payments:'支付订单',refunds:'退款订单',transfers:'转账订单',recharges:'充值订单',withdrawals:'提现订单','food-orders':'外卖订单','collection-records':'收款记录'};
  return <><div className={styles.pageTitle}><h1>订单中心</h1><span>支付、退款、转账、充值、提现、外卖与收款记录统一查询</span></div><Space className={styles.tabs} wrap>{(Object.keys(labels) as OrderKind[]).map(v=><Button key={v} type={kind===v?'primary':'default'} onClick={()=>{setKind(v);setPage(1);setFilters({});setSelected(undefined);}}>{labels[v]}</Button>)}</Space><Card><Form key={kind} layout="inline" onFinish={(v)=>{const range=v.range;setPage(1);setFilters({...v,range:undefined,from:range?.[0]?.format('YYYY-MM-DD'),to:range?.[1]?.format('YYYY-MM-DD')});}}>{kind==='food-orders'?<><Form.Item name="orderNo"><Input allowClear placeholder="外卖订单号" /></Form.Item><Form.Item name="paymentStatus"><Input allowClear placeholder="支付状态" /></Form.Item><Form.Item name="fulfillmentStatus"><Input allowClear placeholder="履约状态" /></Form.Item><Form.Item name="refundStatus"><Input allowClear placeholder="退款状态" /></Form.Item></>:kind==='collection-records'?<><Form.Item name="businessNo"><Input allowClear placeholder="业务单号" /></Form.Item><Form.Item name="type"><Select allowClear placeholder="记录类型" style={{width:130}} options={[{value:'COLLECTION',label:'收款'},{value:'REFUND',label:'退款'}]} /></Form.Item></>:kind==='transfers'?<Form.Item name="transferNo"><Input allowClear placeholder="转账单号" /></Form.Item>:kind==='recharges'?<Form.Item name="rechargeNo"><Input allowClear placeholder="充值单号" /></Form.Item>:kind==='withdrawals'?<Form.Item name="withdrawalNo"><Input allowClear placeholder="提现单号" /></Form.Item>:<><Form.Item name="merchantNo"><Input allowClear placeholder="商户号" /></Form.Item><Form.Item name="name"><Input allowClear placeholder="商户名称" /></Form.Item>{kind==='payments'&&<Form.Item name="appId"><Input allowClear placeholder="应用ID" /></Form.Item>}</>} {kind!=='food-orders'&&<Form.Item name="status"><Input allowClear placeholder="订单状态" /></Form.Item>}<Form.Item name="range"><RangePicker /></Form.Item><Button type="primary" htmlType="submit">查询</Button></Form><Table rowKey={no} loading={query.isLoading} dataSource={query.data?.items} pagination={{current:page,pageSize:20,total:query.data?.total,onChange:setPage,showTotal:total=>`共 ${total} 条`}} columns={columns} scroll={{x:1100}} /></Card><Drawer width={620} title={`${labels[kind]}详情`} open={!!selected} onClose={()=>setSelected(undefined)} loading={detail.isLoading}><JsonDetails value={detail.data} /></Drawer></>;
}

function Wallets() {
  const [tab,setTab]=useState<'wallets'|'ledger'>('wallets'); const [page,setPage]=useState(1); const [filters,setFilters]=useState<Record<string,unknown>>({}); const [wallet,setWallet]=useState<string>(); const [ledger,setLedger]=useState<string>();
  const wallets=useQuery({queryKey:['wallets',page,filters],queryFn:()=>adminApi.wallets({page,size:20,...filters}),enabled:tab==='wallets'});
  const ledgers=useQuery({queryKey:['ledger',page],queryFn:()=>adminApi.ledger({page,size:20}),enabled:tab==='ledger'});
  const walletDetail=useQuery({queryKey:['wallet',wallet],queryFn:()=>adminApi.wallet(wallet!),enabled:!!wallet});
  const bills=useQuery({queryKey:['wallet-bills',wallet],queryFn:()=>adminApi.walletBills(wallet!,{page:1,size:20}),enabled:!!wallet});
  const ledgerDetail=useQuery({queryKey:['ledger-detail',ledger],queryFn:()=>adminApi.ledgerDetail(ledger!),enabled:!!ledger});
  return <><div className={styles.pageTitle}><h1>钱包中心</h1><span>余额账户、账单与复式记账流水</span></div><Space className={styles.tabs}><Button type={tab==='wallets'?'primary':'default'} onClick={()=>{setTab('wallets');setPage(1);}}>钱包账户</Button><Button type={tab==='ledger'?'primary':'default'} onClick={()=>{setTab('ledger');setPage(1);}}>总账流水</Button></Space><Card>{tab==='wallets'?<><Form layout="inline" onFinish={v=>{setPage(1);setFilters(v);}}><Form.Item name="ownerId"><Input allowClear placeholder="所有者ID" /></Form.Item><Form.Item name="status"><Select allowClear placeholder="状态" style={{width:130}} options={['ACTIVE','FROZEN','CLOSED'].map(value=>({value,label:displayValue(value)}))} /></Form.Item><Button type="primary" htmlType="submit">查询</Button></Form><Table rowKey="accountId" dataSource={wallets.data?.items} loading={wallets.isLoading} pagination={{current:page,pageSize:20,total:wallets.data?.total,onChange:setPage}} columns={[{title:'钱包号',dataIndex:'accountNo'},{title:'所有者类型',dataIndex:'ownerType',render:displayValue},{title:'所有者ID',dataIndex:'ownerId'},{title:'账户角色',dataIndex:'accountRole',render:displayValue},{title:'可用余额',dataIndex:'availableAmountCent',render:money},{title:'冻结金额',dataIndex:'frozenAmountCent',render:money},{title:'状态',dataIndex:'status',render:statusTag},{title:'操作',render:(_,r:Wallet)=><Button type="link" onClick={()=>setWallet(r.accountId)}>详情/账单</Button>}]} /></>:<Table rowKey="transactionId" dataSource={ledgers.data?.items} loading={ledgers.isLoading} pagination={{current:page,pageSize:20,total:ledgers.data?.total,onChange:setPage}} columns={[{title:'流水号',dataIndex:'transactionNo'},{title:'业务类型',dataIndex:'businessType',render:displayValue},{title:'业务单号',dataIndex:'businessNo'},{title:'借方总额',dataIndex:'debitTotalAmountCent',render:money},{title:'贷方总额',dataIndex:'creditTotalAmountCent',render:money},{title:'发生时间',dataIndex:'occurredAt',render:time},{title:'操作',render:(_,r:LedgerTransaction)=><Button type="link" onClick={()=>setLedger(r.transactionId)}>详情</Button>}]} />}</Card><Drawer width={720} title="钱包详情与最近账单" open={!!wallet} onClose={()=>setWallet(undefined)} loading={walletDetail.isLoading}><JsonDetails value={walletDetail.data as unknown as Record<string,unknown>} /><h3>最近账单</h3><Table size="small" rowKey={(r)=>String(r.billId??r.id)} pagination={false} dataSource={bills.data?.items} columns={[{title:'时间',dataIndex:'createdAt',render:time},{title:'业务类型',dataIndex:'businessType',render:displayValue},{title:'方向',dataIndex:'direction',render:displayValue},{title:'金额',dataIndex:'amountCent',render:money},{title:'状态',dataIndex:'status',render:statusTag}]} /></Drawer><Drawer width={680} title="总账流水详情" open={!!ledger} onClose={()=>setLedger(undefined)} loading={ledgerDetail.isLoading}><JsonDetails value={ledgerDetail.data} /></Drawer></>;
}

function Backoffice() {
  const {message}=App.useApp(); const [open,setOpen]=useState(false); const [form]=Form.useForm(); const client=useQueryClient();
  const accounts=useQuery({queryKey:['backoffice-accounts'],queryFn:()=>adminApi.accounts({page:1,size:100})});
  const create=useMutation({mutationFn:adminApi.createAccount,onSuccess:()=>{void message.success('后台账号创建成功，可使用手机短信登录');setOpen(false);form.resetFields();void client.invalidateQueries({queryKey:['backoffice-accounts']});},onError:(e)=>message.error((e as Error).message)});
  const rows=(accounts.data?.items??[]).filter(a=>a.roles.some(r=>['system_super_admin','system_account_admin','system_auditor','platform_admin'].includes(r)));
  const roles=[['system_super_admin','超级管理员','拥有管理端全部读写权限，可创建后台账号与调整角色'],['system_account_admin','账号管理员','负责账号解锁、停用、强退与登录方式重置'],['system_auditor','只读审计员','只读查看业务数据、登录记录与管理操作'],['platform_admin','运营账号','属于运营平台账号，不进入系统管理平台']];
  return <><div className={styles.pageTitle}><h1>后台账号</h1><span>分级授权、独立登录、全程留痕</span></div><div className={styles.roleCards}>{roles.map(([key,title,desc])=><Card key={key}><Tag color={key==='system_super_admin'?'red':'blue'}>{title}</Tag><p>{desc}</p></Card>)}</div><Card title="后台账号清单" extra={<Button type="primary" onClick={()=>setOpen(true)}>创建后台账号</Button>}><Table rowKey="userId" loading={accounts.isLoading} dataSource={rows} pagination={false} columns={[{title:'MiniPay号',dataIndex:'minipayNo'},{title:'名称',dataIndex:'displayName'},{title:'手机',dataIndex:'maskedMobile'},{title:'角色',dataIndex:'roles',render:(v:string[])=><Space wrap>{v.map(r=><Tag key={r}>{roleName[r]??r}</Tag>)}</Space>},{title:'状态',dataIndex:'status',render:statusTag},{title:'创建时间',dataIndex:'createdAt',render:time}]} /></Card><Modal title="创建后台账号" open={open} onCancel={()=>setOpen(false)} onOk={()=>form.submit()} confirmLoading={create.isPending}><Form form={form} layout="vertical" onFinish={v=>create.mutate(v)}><Form.Item name="mobile" label="手机号" rules={[{required:true,pattern:/^1[3-9]\d{9}$/,message:'请输入正确手机号'}]}><Input maxLength={11} /></Form.Item><Form.Item name="displayName" label="显示名称" rules={[{required:true}]}><Input maxLength={64} /></Form.Item><Form.Item name="role" label="角色" rules={[{required:true}]}><Select options={roles.map(([value,title])=>({value,label:title}))} /></Form.Item></Form></Modal></>;
}

const csvCell=(v:unknown)=>{let s=String(v??'');if(/^[=+\-@]/.test(s))s=`'${s}`;return `"${s.replace(/"/g,'""')}"`;};
function Audits() {
  const {message}=App.useApp(); const [form]=Form.useForm(); const [page,setPage]=useState(1); const [filters,setFilters]=useState<Record<string,unknown>>({}); const [detail,setDetail]=useState<Audit>(); const [exporting,setExporting]=useState(false);
  const q=useQuery({queryKey:['audits',page,filters],queryFn:()=>adminApi.audits({page,size:20,...filters})});
  const exportCsv=async()=>{setExporting(true);try{const first=await adminApi.audits({page:1,size:100,...filters});let all=[...first.items];for(let p=2;p<=Math.ceil(first.total/100);p++)all=all.concat((await adminApi.audits({page:p,size:100,...filters})).items);const keys=['occurredAt','actorUserId','action','targetType','targetId','result','reason','requestId'] as const;const csv=['时间,操作人ID,操作,目标类型,目标ID,结果,原因,请求号',...all.map(r=>keys.map(k=>csvCell(r[k])).join(','))].join('\r\n');const url=URL.createObjectURL(new Blob(['\ufeff',csv],{type:'text/csv;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download=`minipay-audits-${dayjs().format('YYYYMMDD-HHmmss')}.csv`;a.click();URL.revokeObjectURL(url);void message.success(`已导出 ${all.length} 条记录`);}catch(e){void message.error((e as Error).message);}finally{setExporting(false);}};
  return <>
    <div className={styles.pageTitle}><h1>安全审计</h1><span>查询并导出所有敏感管理操作</span></div>
    <Card>
      <Form form={form} layout="inline" onFinish={v=>{const range=v.range;setPage(1);setFilters({...v,range:undefined,from:range?.[0]?.toISOString(),to:range?.[1]?.endOf('day').toISOString()});}}>
        <Form.Item name="actorUserId"><Input allowClear placeholder="操作人ID" /></Form.Item>
        <Form.Item name="action"><Input allowClear placeholder="操作类型" /></Form.Item>
        <Form.Item name="targetType"><Input allowClear placeholder="目标类型" /></Form.Item>
        <Form.Item name="result"><Select allowClear placeholder="结果" style={{width:120}} options={['SUCCEEDED','FAILED'].map(value=>({value,label:displayValue(value)}))} /></Form.Item>
        <Form.Item name="requestId"><Input allowClear placeholder="请求号" /></Form.Item>
        <Form.Item name="range"><RangePicker showTime /></Form.Item>
        <Button type="primary" htmlType="submit">查询</Button>
        <Button onClick={()=>{form.resetFields();setFilters({});setPage(1);}}>清空</Button>
        <Button icon={<DownloadOutlined />} loading={exporting} onClick={()=>void exportCsv()}>导出结果</Button>
      </Form>
      {q.isError&&<Alert showIcon type="error" message="审计记录加载失败" description={(q.error as Error).message} />}
      <Table rowKey="auditId" loading={q.isLoading} dataSource={q.data?.items} pagination={{current:page,pageSize:20,total:q.data?.total,onChange:setPage,showTotal:total=>`共 ${total} 条`,showSizeChanger:false}} columns={[{title:'时间',dataIndex:'occurredAt',render:time},{title:'操作',dataIndex:'action',render:displayValue},{title:'目标类型',dataIndex:'targetType',render:displayValue},{title:'目标ID',dataIndex:'targetId'},{title:'结果',dataIndex:'result',render:statusTag},{title:'原因',dataIndex:'reason',ellipsis:true},{title:'操作',render:(_,r:Audit)=><Button type="link" onClick={()=>setDetail(r)}>详情</Button>}]} />
    </Card>
    <Drawer width={600} title="审计记录详情" open={!!detail} onClose={()=>setDetail(undefined)}><JsonDetails value={detail as unknown as Record<string,unknown>} /></Drawer>
  </>;
}

function SecurityCenter() {
  const [page,setPage]=useState(1); const health=useQuery({queryKey:['system-health'],queryFn:adminApi.systemHealth,refetchInterval:30_000,retry:1}); const logins=useQuery({queryKey:['login-audits',page],queryFn:()=>adminApi.loginAudits({page:page-1,size:20})});
  return <><div className={styles.pageTitle}><h1>登录安全</h1><span>服务健康与管理员认证记录</span></div><Alert className={styles.roleBanner} showIcon type={health.data?.status==='UP'?'success':'warning'} message={health.data?.status==='UP'?'核心服务运行正常':'部分服务暂不可用或检测超时'} description="状态每 30 秒自动更新；仅展示可公开的运行状态，不暴露内部配置。" /><div className={styles.healthGrid}>{(health.data?.services??[]).map(service=><Card key={service.code} loading={health.isLoading}><div className={styles.healthCard}><div><strong>{service.name}</strong><small>{service.latencyMs} ms</small></div>{statusTag(service.status)}</div></Card>)}</div><Card title="管理员登录记录"><Table rowKey="auditId" loading={logins.isLoading} dataSource={logins.data?.items} pagination={{current:page,pageSize:20,total:logins.data?.total,onChange:setPage}} columns={[{title:'登录时间',dataIndex:'occurredAt',render:time},{title:'管理员',dataIndex:'displayName',render:v=>v||'-'},{title:'认证方式',dataIndex:'authenticationMethod',render:v=><Tag color="blue">{displayValue(v||'SMS')}</Tag>},{title:'结果',dataIndex:'result',render:statusTag},{title:'请求号',dataIndex:'requestId'}]} /></Card></>;
}

function OwnPasswordForm() {
  const { message } = App.useApp();
  const [form] = Form.useForm<{ newPassword: string; confirmPassword: string }>();
  const mutation = useMutation({
    mutationFn: (values: { newPassword: string }) => adminApi.ownPassword(values.newPassword),
    onSuccess: () => {
      void message.success('登录密码已更新，请使用新密码重新登录');
      form.resetFields();
      window.setTimeout(() => void submitAdminLogout(), 600);
    },
    onError: (cause) => void message.error((cause as Error).message || '登录密码更新失败')
  });
  return <div style={{marginTop:24}}><h3>设置或修改登录密码</h3><Alert showIcon type="warning" message="修改后当前授权会被撤销，需要使用新密码重新登录。" /><Form form={form} layout="vertical" style={{marginTop:16,maxWidth:440}} onFinish={(values)=>mutation.mutate(values)}><Form.Item name="newPassword" label="新登录密码" rules={[{required:true,message:'请输入新登录密码'},{pattern:/^(?=.*[A-Za-z])(?=.*\d)[\x21-\x7E]{12,20}$/,message:'请输入 12 至 20 位密码，且至少包含字母和数字'}]}><Input.Password autoComplete="new-password" /></Form.Item><Form.Item name="confirmPassword" label="确认新密码" dependencies={['newPassword']} rules={[{required:true,message:'请再次输入新密码'},({getFieldValue})=>({validator(_,value){return !value||getFieldValue('newPassword')===value?Promise.resolve():Promise.reject(new Error('两次输入的密码不一致'));}})]}><Input.Password autoComplete="new-password" /></Form.Item><Button type="primary" htmlType="submit" loading={mutation.isPending}>保存登录密码</Button></Form></div>;
}

function Settings({roles,displayName}:{roles:Role[];displayName?:string}) { return <><div className={styles.pageTitle}><h1>个人设置</h1><span>当前管理员身份与安全认证方式</span></div><Card title="账号与认证" style={{maxWidth:760}}><Alert showIcon type="info" message="管理端支持密码登录与短信验证码登录" description="管理员账号与运营账号入口完全分离；密码修改后会撤销旧授权并要求重新登录。" /><Descriptions className={styles.settingsDetails} bordered column={1} items={[{key:'name',label:'当前账号',children:displayName??'-'},{key:'roles',label:'权限角色',children:<Space wrap>{roles.map(r=><Tag key={r}>{roleName[r]??r}</Tag>)}</Space>},{key:'login',label:'登录方式',children:<Space><Tag color="blue">登录密码</Tag><Tag color="cyan">短信验证码</Tag></Space>},{key:'entry',label:'独立入口',children:new URL('login',ADMIN_WEB_PUBLIC_URL).toString()}]} /><OwnPasswordForm /><Button style={{marginTop:24}} type="primary" danger onClick={()=>void submitAdminLogout()}>安全退出当前会话</Button></Card></>; }
