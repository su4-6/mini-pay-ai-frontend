import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocation, useNavigate } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Empty,
  Flex,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography
} from 'antd';
import type { MerchantStatus, MerchantType, OpsMerchant } from '@minipay/api-contracts';
import { ApiProblemError, createIdempotencyKey } from '@minipay/api-client';
import dayjs from 'dayjs';
import { AuthGate } from '../components/AuthGate';
import LocationPicker, { type LocationValue } from '../components/LocationPicker';
import ShopImagesUpload from '../components/ShopImagesUpload';
import { getSession } from '../services/auth';
import {
  changeMerchantStatus,
  createMerchant,
  deleteMerchant,
  freezeMerchant,
  getMerchant,
  getMerchants,
  unfreezeMerchant,
  updateMerchant
} from '../services/ops';
import styles from './merchants.module.less';

interface MerchantFormValues {
  name: string;
  shortName: string;
  contactName: string;
  contactMobile: string;
  contactEmail?: string;
  remark?: string;
  merchantType: MerchantType;
  mccCode?: string;
  address?: string;
  location?: LocationValue;
  shopImages?: string;
  status?: MerchantStatus;
}
interface Filters {
  merchantNo?: string;
  name?: string;
  contactMobile?: string;
  status?: MerchantStatus;
}

const blockedReasonLabels: Record<string, string> = {
  MERCHANT_HAS_APPLICATIONS: '该商户已有应用，不能删除',
  MERCHANT_HAS_TRANSACTIONS: '该商户已有交易，不能删除'
};

const statusLabels: Record<MerchantStatus, { text: string; color: string }> = {
  ACTIVE: { text: '已启用', color: 'green' },
  DISABLED: { text: '已禁用', color: 'default' },
  FROZEN: { text: '已冻结', color: 'red' }
};

const merchantTypeLabels: Record<MerchantType, string> = {
  PERSONAL: '个人经营',
  INDIVIDUAL: '个体工商',
  ENTERPRISE: '企业'
};

const maskPhone = (phone?: string) =>
  phone && phone.length >= 7 ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : (phone ?? '-');

function errorMessage(error: unknown): string {
  if (error instanceof ApiProblemError) {
    if (error.problem.code === 'MERCHANT_VERSION_CONFLICT') return '商户已被其他运营人员修改，请刷新后重试。';
    if (error.problem.code === 'MERCHANT_HAS_DEPENDENCIES') return '商户存在应用或交易依赖，不能删除。';
    if (error.problem.code === 'MERCHANT_NOT_FREEZABLE') return '只有正常营业的商户可以被冻结。';
    if (error.problem.code === 'MERCHANT_NOT_UNFREEZABLE') return '只有已冻结的商户可以解冻。';
    if (error.problem.code === 'IDENTITY_UNAVAILABLE') return '账户服务暂不可用，请稍后重试。';
    if (error.problem.code === 'MERCHANT_OWNER_DISABLED') return '该手机号对应的账户已被停用，无法开通商户。';
    return error.problem.detail ?? error.problem.title;
  }
  return '操作失败，请稍后重试。';
}

function MerchantsContent() {
  const navigate = useNavigate();
  const location = useLocation();
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [filters, setFilters] = useState<Filters>({});
  const [draftFilters, setDraftFilters] = useState<Filters>({});
  // 支持从应用列表跳转进入时（/merchants?merchantId=xxx）自动打开商户详情
  const [selectedId, setSelectedId] = useState<string | undefined>(
    () => new URLSearchParams(location.search).get('merchantId') ?? undefined
  );
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<OpsMerchant>();
  const [freezeTarget, setFreezeTarget] = useState<OpsMerchant>();
  const [freezeReason, setFreezeReason] = useState('');
  const [form] = Form.useForm<MerchantFormValues>();
  const session = useQuery({ queryKey: ['session'], queryFn: getSession, staleTime: 30_000 });
  const canWrite = session.data?.admin?.permissions.includes('ops.merchant.write') ?? false;
  const canReadApplications = session.data?.admin?.permissions.includes('ops.application.read') ?? false;

  const merchants = useQuery({
    queryKey: [
      'ops-merchants', page, size,
      filters.merchantNo, filters.name, filters.contactMobile, filters.status
    ],
    queryFn: () => getMerchants({ page, size, ...filters })
  });
  const detail = useQuery({
    queryKey: ['ops-merchant', selectedId],
    queryFn: () => getMerchant(selectedId!),
    enabled: Boolean(selectedId)
  });

  useEffect(() => {
    if (formOpen) {
      form.setFieldsValue(editing ? {
        name: editing.name,
        shortName: editing.shortName,
        contactName: editing.contactName ?? '',
        contactMobile: editing.contactMobile ?? '',
        contactEmail: editing.contactEmail ?? undefined,
        remark: editing.remark ?? undefined,
        merchantType: editing.merchantType,
        mccCode: editing.mccCode ?? undefined,
        address: editing.address ?? undefined,
        location: editing.latitude != null && editing.longitude != null
          ? { latitude: editing.latitude, longitude: editing.longitude }
          : undefined,
        shopImages: editing.shopImages ?? undefined
      } : { status: 'ACTIVE', merchantType: 'PERSONAL' });
    }
  }, [editing, form, formOpen]);

  const refreshMerchantData = async (merchantId?: string) => {
    await queryClient.invalidateQueries({ queryKey: ['ops-merchants'] });
    if (merchantId) await queryClient.invalidateQueries({ queryKey: ['ops-merchant', merchantId] });
    await queryClient.invalidateQueries({ queryKey: ['ops-dashboard'] });
  };

  const save = useMutation({
    mutationFn: (values: MerchantFormValues) => {
      const key = createIdempotencyKey();
      const { location, ...rest } = values;
      const input = {
        ...rest,
        latitude: location?.latitude,
        longitude: location?.longitude
      };
      return editing
        ? updateMerchant(editing, input, key)
        : createMerchant(input, key);
    },
    onSuccess: async (merchant, values) => {
      setFormOpen(false);
      form.resetFields();
      await refreshMerchantData(merchant.merchantId);
      const phone = maskPhone((values as MerchantFormValues | undefined)?.contactMobile);
      void message.success(editing ? '商户资料已更新' : `商户已创建，并已归属到手机号 ${phone} 的账号`);
    },
    onError: (error) => void message.error(errorMessage(error))
  });

  const changeStatus = useMutation({
    mutationFn: ({ merchant, status }: { merchant: OpsMerchant; status: MerchantStatus }) =>
      changeMerchantStatus(merchant, status, createIdempotencyKey()),
    onSuccess: async (merchant) => {
      await refreshMerchantData(merchant.merchantId);
      void message.success(merchant.status === 'ACTIVE' ? '商户已启用' : '商户已禁用');
    },
    onError: (error) => void message.error(errorMessage(error))
  });

  const freeze = useMutation({
    mutationFn: ({ merchant, reason }: { merchant: OpsMerchant; reason: string }) =>
      freezeMerchant(merchant, reason, createIdempotencyKey()),
    onSuccess: async (merchant) => {
      setFreezeTarget(undefined);
      setFreezeReason('');
      await refreshMerchantData(merchant.merchantId);
      void message.success('商户已冻结');
    },
    onError: (error) => void message.error(errorMessage(error))
  });

  const unfreeze = useMutation({
    mutationFn: (merchant: OpsMerchant) => unfreezeMerchant(merchant, createIdempotencyKey()),
    onSuccess: async (merchant) => {
      await refreshMerchantData(merchant.merchantId);
      void message.success('商户已解冻');
    },
    onError: (error) => void message.error(errorMessage(error))
  });

  const remove = useMutation({
    mutationFn: (merchant: OpsMerchant) => deleteMerchant(merchant, createIdempotencyKey()),
    onSuccess: async () => {
      setSelectedId(undefined);
      await refreshMerchantData();
      void message.success('商户已删除');
    },
    onError: (error) => void message.error(errorMessage(error))
  });

  const confirmStatusChange = (merchant: OpsMerchant) => {
    const target: MerchantStatus = merchant.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    modal.confirm({
      title: target === 'ACTIVE' ? '确认启用商户？' : '确认禁用商户？',
      content: target === 'DISABLED' ? '禁用后该商户不能发起新的支付订单。' : '启用后商户将恢复可用状态。',
      okText: target === 'ACTIVE' ? '启用' : '禁用',
      okButtonProps: { danger: target === 'DISABLED' },
      onOk: async () => { await changeStatus.mutateAsync({ merchant, status: target }); }
    });
  };

  const confirmUnfreeze = (merchant: OpsMerchant) => {
    modal.confirm({
      title: '确认解冻商户？',
      content: '解冻后商户恢复为正常营业状态。',
      okText: '解冻',
      onOk: async () => { await unfreeze.mutateAsync(merchant); }
    });
  };

  const confirmDelete = (merchant: OpsMerchant) => {
    modal.confirm({
      title: '确认删除商户？',
      content: `将永久删除“${merchant.name}”。该操作仅允许用于没有应用和交易的商户。`,
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: async () => { await remove.mutateAsync(merchant); }
    });
  };

  const columns = useMemo<TableColumnsType<OpsMerchant>>(() => [
    { title: '商户号', dataIndex: 'merchantNo', width: 150 },
    { title: '商户名称', dataIndex: 'name', width: 140 },
    { title: '商户简称', dataIndex: 'shortName', width: 110 },
    {
      title: '联系人手机号', dataIndex: 'contactMobile', width: 130,
      render: (value: string | null) => value || '-'
    },
    {
      title: '账户状态', dataIndex: 'accountLinked', width: 90, align: 'center',
      render: (linked: boolean) => linked
        ? <Tag color="green">已绑定</Tag> : <Tag color="orange">未绑定</Tag>
    },
    {
      title: '状态', dataIndex: 'status', width: 82,
      render: (status: MerchantStatus) => {
        const label = statusLabels[status];
        return <Tag color={label.color}>{label.text}</Tag>;
      }
    },
    { title: '应用数量', dataIndex: 'applicationCount', width: 82, align: 'center' },
    { title: '创建时间', dataIndex: 'createdAt', width: 150, render: (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm:ss') },
    { title: '更新时间', dataIndex: 'updatedAt', width: 150, render: (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm:ss') },
    {
      title: '操作', key: 'actions', width: 320,
      render: (_, merchant) => (
        <Space className={styles.actions} size={[2, 0]} wrap>
          <Button type="link" onClick={() => setSelectedId(merchant.merchantId)}>详情</Button>
          <Tooltip title={canReadApplications ? undefined : '缺少应用读取权限'}>
            <span><Button
              type="link"
              disabled={!canReadApplications}
              onClick={() => navigate(`/applications?merchantId=${merchant.merchantId}`)}
            >应用配置</Button></span>
          </Tooltip>
          {canWrite ? <Button type="link" onClick={() => { setEditing(merchant); setFormOpen(true); }}>编辑</Button> : null}
          {canWrite && merchant.status === 'ACTIVE' ? (
            <>
              <Button type="link" danger onClick={() => confirmStatusChange(merchant)}>停用</Button>
              <Button type="link" danger onClick={() => { setFreezeTarget(merchant); setFreezeReason(''); }}>冻结</Button>
            </>
          ) : null}
          {canWrite && merchant.status === 'DISABLED' ? (
            <Button type="link" onClick={() => confirmStatusChange(merchant)}>启用</Button>
          ) : null}
          {canWrite && merchant.status === 'FROZEN' ? (
            <Button type="link" onClick={() => confirmUnfreeze(merchant)}>解冻</Button>
          ) : null}
          {canWrite ? (
            <Tooltip title={merchant.deletable ? undefined : blockedReasonLabels[merchant.deletionBlockedReason ?? '']}>
              <span><Button type="link" danger disabled={!merchant.deletable} onClick={() => confirmDelete(merchant)}>删除</Button></span>
            </Tooltip>
          ) : null}
        </Space>
      )
    }
  ], [canReadApplications, canWrite, navigate]);

  return (
    <Card className={styles.card}>
      <Flex className={styles.header} justify="space-between" align="flex-start" gap={16} wrap>
        <div>
          <Typography.Title level={3}>商户管理</Typography.Title>
          <Typography.Paragraph type="secondary">管理平台商户资料、启停与冻结状态；商户号由系统自动生成。</Typography.Paragraph>
        </div>
        {canWrite ? <Button type="primary" onClick={() => { setEditing(undefined); setFormOpen(true); }}>新建商户</Button> : null}
      </Flex>

      <Flex className={styles.filters} gap={12} wrap>
        <Input
          allowClear
          placeholder="输入商户号"
          value={draftFilters.merchantNo}
          onChange={(event) => setDraftFilters((value) => ({ ...value, merchantNo: event.target.value }))}
          onPressEnter={() => { setPage(0); setFilters({ ...draftFilters }); }}
        />
        <Input
          allowClear
          placeholder="输入商户名称"
          value={draftFilters.name}
          onChange={(event) => setDraftFilters((value) => ({ ...value, name: event.target.value }))}
          onPressEnter={() => { setPage(0); setFilters({ ...draftFilters }); }}
        />
        <Input
          allowClear
          placeholder="输入联系人手机号"
          inputMode="numeric"
          maxLength={11}
          value={draftFilters.contactMobile}
          onChange={(event) => setDraftFilters((value) => ({ ...value, contactMobile: event.target.value }))}
          onPressEnter={() => { setPage(0); setFilters({ ...draftFilters }); }}
        />
        <Select
          allowClear
          placeholder="全部状态"
          value={draftFilters.status}
          options={[
            { label: '已启用', value: 'ACTIVE' },
            { label: '已禁用', value: 'DISABLED' },
            { label: '已冻结', value: 'FROZEN' }
          ]}
          onChange={(status) => setDraftFilters((value) => ({ ...value, status }))}
        />
        <Button type="primary" onClick={() => { setPage(0); setFilters({ ...draftFilters }); }}>查询</Button>
        <Button onClick={() => { setDraftFilters({}); setFilters({}); setPage(0); }}>重置</Button>
      </Flex>

      {merchants.isError ? (
        <Alert showIcon type="error" title="商户列表加载失败" action={<Button onClick={() => void merchants.refetch()}>重试</Button>} />
      ) : (
        <Table<OpsMerchant>
          className={styles.table}
          rowKey="merchantId"
          columns={columns}
          dataSource={merchants.data?.items ?? []}
          loading={merchants.isPending || merchants.isFetching}
          tableLayout="fixed"
          locale={{ emptyText: <Empty description="没有符合条件的商户" /> }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: merchants.data?.total ?? 0,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50, 100],
            showTotal: (total) => `共 ${total} 条`,
            onChange: (nextPage, nextSize) => { setPage(nextPage - 1); setSize(nextSize); }
          }}
        />
      )}

      <Drawer
        open={formOpen}
        title={editing ? '编辑商户' : '新建商户'}
        size={760}
        onClose={() => { setFormOpen(false); form.resetFields(); }}
        destroyOnHidden
        footer={(
          <Flex justify="flex-end" gap={8}>
            <Button onClick={() => { setFormOpen(false); form.resetFields(); }}>取消</Button>
            <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>
              {editing ? '保存' : '创建'}
            </Button>
          </Flex>
        )}
      >
        <Form form={form} layout="vertical" onFinish={(values) => save.mutate(values)}>
          {editing && !editing.profileComplete ? (
            <Alert className={styles.profileAlert} showIcon type="warning" title="商户资料待完善" description="请补齐联系人和手机号后保存。" />
          ) : null}
          <Row gutter={16}>
            <Col xs={24} md={12}><Form.Item label="商户名称" name="name" rules={[
              { required: true, message: '请输入商户名称' },
              { min: 2, max: 64, message: '商户名称长度为 2～64 个字符' }
            ]}><Input maxLength={64} showCount placeholder="请输入商户注册名称" /></Form.Item></Col>
            <Col xs={24} md={12}><Form.Item label="商户简称" name="shortName" rules={[
              { required: true, message: '请输入商户简称' },
              { min: 2, max: 32, message: '商户简称长度为 2～32 个字符' }
            ]}><Input maxLength={32} showCount placeholder="请输入对外展示简称" /></Form.Item></Col>
            <Col xs={24} md={12}><Form.Item label="商户类型" name="merchantType" rules={[
              { required: true, message: '请选择商户类型' }
            ]}>
              <Select options={[
                { label: '个人经营', value: 'PERSONAL' },
                { label: '个体工商', value: 'INDIVIDUAL' },
                { label: '企业', value: 'ENTERPRISE' }
              ]} />
            </Form.Item></Col>
            <Col xs={24} md={12}><Form.Item label="经营类目编码（MCC）" name="mccCode" rules={[
              { max: 16, message: 'MCC 编码不能超过 16 个字符' }
            ]}><Input maxLength={16} placeholder="选填，如 5811" /></Form.Item></Col>
            <Col xs={24} md={12}><Form.Item label="联系人姓名" name="contactName" rules={[
              { required: true, message: '请输入联系人姓名' },
              { min: 2, max: 64, message: '联系人姓名长度为 2～64 个字符' }
            ]}><Input maxLength={64} placeholder="请输入联系人姓名" /></Form.Item></Col>
            <Col xs={24} md={12}><Form.Item label="联系人手机号" name="contactMobile" extra="商户将归属到该手机号的账号，商户用此手机号登录后即可看到此商户。" rules={[
              { required: true, message: '请输入联系人手机号' },
              { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的中国大陆手机号' }
            ]}><Input inputMode="numeric" maxLength={11} placeholder="请输入 11 位手机号" /></Form.Item></Col>
            <Col xs={24} md={12}><Form.Item label="联系人邮箱" name="contactEmail" rules={[
              { type: 'email', message: '请输入正确的邮箱地址' },
              { max: 254, message: '邮箱地址不能超过 254 个字符' }
            ]}><Input maxLength={254} placeholder="选填" /></Form.Item></Col>
            <Col xs={24} md={12}>{editing ? (
              <Form.Item label="当前状态"><Input value={statusLabels[editing.status].text} disabled /></Form.Item>
            ) : (
              <Form.Item label="初始状态" name="status">
                <Select options={[{ label: '已启用', value: 'ACTIVE' }, { label: '已禁用', value: 'DISABLED' }]} />
              </Form.Item>
            )}</Col>
            <Col span={24}><Form.Item label="经营地址" name="address" rules={[
              { max: 200, message: '经营地址不能超过 200 个字符' }
            ]}><Input maxLength={200} placeholder="选填，可在地图上选点自动填充" /></Form.Item></Col>
            <Col span={24}>
              <Form.Item label="经营位置（地图选点）" name="location">
                <LocationPicker
                  onAddressChange={(address) => form.setFieldValue('address', address)}
                />
              </Form.Item>
            </Col>
            <Col span={24}><Form.Item label="店铺图片" name="shopImages" valuePropName="value">
              <ShopImagesUpload />
            </Form.Item></Col>
            <Col span={24}><Form.Item label="备注" name="remark" rules={[{ max: 500, message: '备注不能超过 500 个字符' }]}>
              <Input.TextArea rows={4} maxLength={500} showCount placeholder="选填" />
            </Form.Item></Col>
          </Row>
        </Form>
      </Drawer>

      <Drawer title="商户详情" size={520} open={Boolean(selectedId)} onClose={() => {
        setSelectedId(undefined);
        if (location.search) navigate('/merchants', { replace: true });
      }}>
        {detail.isError ? <Alert type="error" showIcon title="商户详情加载失败" /> : (
          <Descriptions column={1} bordered size="small" items={detail.data ? [
            { key: 'merchantNo', label: '商户号', children: detail.data.merchantNo },
            { key: 'name', label: '商户名称', children: detail.data.name },
            { key: 'shortName', label: '商户简称', children: detail.data.shortName },
            { key: 'merchantType', label: '商户类型', children: merchantTypeLabels[detail.data.merchantType] ?? '-' },
            { key: 'mccCode', label: '经营类目', children: detail.data.mccCode || '-' },
            { key: 'address', label: '经营地址', children: detail.data.address || '-' },
            { key: 'shopImages', label: '店铺图片', children: detail.data.shopImages
              ? <ShopImagesUpload value={detail.data.shopImages} disabled />
              : '-' },
            { key: 'contactName', label: '联系人姓名', children: detail.data.contactName || '-' },
            { key: 'contactMobile', label: '联系人手机号', children: detail.data.contactMobile || '-' },
            { key: 'contactEmail', label: '联系人邮箱', children: detail.data.contactEmail || '-' },
            { key: 'remark', label: '备注', children: detail.data.remark || '-' },
            { key: 'profile', label: '资料状态', children: detail.data.profileComplete ? <Tag color="green">完整</Tag> : <Tag color="orange">待完善</Tag> },
            {
              key: 'status', label: '状态', children: (
                <Space size={8}>
                  <Tag color={statusLabels[detail.data.status].color}>{statusLabels[detail.data.status].text}</Tag>
                  {detail.data.status === 'FROZEN' && detail.data.freezeReason ? `（${detail.data.freezeReason}）` : null}
                </Space>
              )
            },
            {
              key: 'account', label: '账户状态', children: detail.data.accountLinked
                ? <Space size={8}><Tag color="green">已绑定</Tag>{detail.data.ownerUserId ? <Typography.Text type="secondary">{detail.data.ownerUserId.slice(0, 8)}…</Typography.Text> : null}</Space>
                : <Tag color="orange">未绑定</Tag>
            },
            { key: 'applications', label: '应用数量', children: detail.data.applicationCount },
            { key: 'createdAt', label: '创建时间', children: dayjs(detail.data.createdAt).format('YYYY-MM-DD HH:mm:ss') },
            { key: 'updatedAt', label: '更新时间', children: dayjs(detail.data.updatedAt).format('YYYY-MM-DD HH:mm:ss') },
            { key: 'delete', label: '删除条件', children: detail.data.deletable ? '允许删除' : blockedReasonLabels[detail.data.deletionBlockedReason ?? ''] }
          ] : []} />
        )}
      </Drawer>

      <Modal
        open={Boolean(freezeTarget)}
        title="冻结商户"
        okText="冻结"
        okButtonProps={{ danger: true, loading: freeze.isPending }}
        onOk={() => freezeTarget && freeze.mutate({ merchant: freezeTarget, reason: freezeReason.trim() })}
        onCancel={() => { setFreezeTarget(undefined); setFreezeReason(''); }}
      >
        <Typography.Paragraph type="secondary">
          冻结后商户不能发起新的收款，历史余额仍可提现。请输入冻结原因。
        </Typography.Paragraph>
        <Input.TextArea
          rows={3}
          maxLength={200}
          showCount
          value={freezeReason}
          onChange={(event) => setFreezeReason(event.target.value)}
          placeholder="请输入冻结原因（必填，最多 200 字）"
        />
      </Modal>
    </Card>
  );
}

export default function MerchantsPage() {
  return <AuthGate routeKey="merchants"><MerchantsContent /></AuthGate>;
}
