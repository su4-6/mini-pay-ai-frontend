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
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography
} from 'antd';
import type {
  ApplicationDeletionBlockedReason,
  ApplicationStatus,
  MerchantStatus,
  OpsApplication,
  OpsMerchant
} from '@minipay/api-contracts';
import { ApiProblemError, createIdempotencyKey } from '@minipay/api-client';
import dayjs from 'dayjs';
import { AuthGate } from '../components/AuthGate';
import { getSession } from '../services/auth';
import {
  changeApplicationStatus,
  createApplication,
  deleteApplication,
  fetchAllForExport,
  getApplication,
  getApplicationSummary,
  getApplications,
  getMerchant,
  getMerchants,
  updateApplication
} from '../services/ops';
import styles from './applications.module.less';

interface Filters {
  appId?: string;
  name?: string;
  merchantId?: string;
  status?: ApplicationStatus;
  unavailable?: boolean;
}

interface ApplicationFormValues {
  merchantId: string;
  name: string;
  status: ApplicationStatus;
}

const blockedReasonLabels: Record<ApplicationDeletionBlockedReason, string> = {
  APPLICATION_MUST_BE_DISABLED: '请先停用应用再删除',
  APPLICATION_HAS_TRANSACTIONS: '该应用已有交易，不能删除',
  APPLICATION_HAS_DEPENDENCIES: '该应用存在需保留的业务记录，不能删除'
};

function applicationError(error: unknown): string {
  if (error instanceof ApiProblemError) {
    const labels: Record<string, string> = {
      APPLICATION_VERSION_CONFLICT: '应用已被其他运营人员修改，请刷新后重试。',
      APPLICATION_NAME_CONFLICT: '该商户下已存在同名应用。',
      APPLICATION_MUST_BE_DISABLED: '请先停用应用再删除。',
      APPLICATION_HAS_TRANSACTIONS: '该应用已有交易，只能保持停用。',
      APPLICATION_HAS_DEPENDENCIES: '该应用存在需保留的业务记录，不能删除。',
      MERCHANT_NOT_ACTIVE: '商户需为正常营业状态，当前无法创建或启用应用。'
    };
    return labels[error.problem.code] ?? error.problem.detail ?? error.problem.title;
  }
  return '操作失败，请稍后重试。';
}

function merchantOption(merchant: OpsMerchant) {
  return {
    value: merchant.merchantId,
    label: `${merchant.name}（${merchant.merchantNo}）`,
    merchant
  };
}

function availabilityInfo(application: OpsApplication): { color: string; reasons: string[] } {
  const { status, merchantStatus } = application;
  if (status === 'ACTIVE' && merchantStatus === 'ACTIVE') {
    return { color: 'green', reasons: [] };
  }
  const reasons: string[] = [];
  if (status === 'DISABLED') reasons.push('应用已停用');
  if (merchantStatus !== 'ACTIVE') {
    reasons.push(merchantStatus === 'FROZEN' ? '所属商户已冻结' : '所属商户已停用');
  }
  return { color: merchantStatus === 'FROZEN' ? 'red' : 'orange', reasons };
}

function merchantStatusLabel(status: MerchantStatus): string {
  return status === 'ACTIVE' ? '已启用' : status === 'FROZEN' ? '已冻结' : '已停用';
}

function exportApplicationsCsv(
  items: OpsApplication[],
  options: { total: number; truncated: boolean }
) {
  const headers = [
    'MiniPay AppID', '应用名称', '所属商户', '商户号',
    '应用状态', '商户状态', '可用性', '近30天交易', '创建时间', '更新时间'
  ];
  const rows = items.map((app) => [
    app.appId, app.name, app.merchantName, app.merchantNo,
    app.status === 'ACTIVE' ? '已启用' : '已停用',
    merchantStatusLabel(app.merchantStatus),
    app.status === 'ACTIVE' && app.merchantStatus === 'ACTIVE' ? '可用' : '不可用',
    app.recentTransactionCount ?? 0,
    app.createdAt, app.updatedAt
  ]);
  const lines = [headers, ...rows]
    .map((row) => row.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(','))
    .join('\n');
  const full = options.truncated
    ? `${lines}\n"共 ${options.total} 条数据，仅导出前 ${items.length} 条"`
    : lines;
  const blob = new Blob(['﻿' + full], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `applications-${dayjs().format('YYYY-MM-DD')}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}

function ApplicationsContent() {
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message, modal } = App.useApp();
  const initialMerchantId = useMemo(
    () => new URLSearchParams(location.search).get('merchantId') ?? undefined,
    [location.search]
  );
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [filters, setFilters] = useState<Filters>(() => ({ merchantId: initialMerchantId }));
  const [draftFilters, setDraftFilters] = useState<Filters>(() => ({ merchantId: initialMerchantId }));
  const [merchantSearch, setMerchantSearch] = useState('');
  const [selectedId, setSelectedId] = useState<string>();
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<OpsApplication>();
  const [selectedApps, setSelectedApps] = useState<OpsApplication[]>([]);
  const [exporting, setExporting] = useState(false);
  const [form] = Form.useForm<ApplicationFormValues>();
  const formMerchantId = Form.useWatch('merchantId', form);

  const session = useQuery({ queryKey: ['session'], queryFn: getSession, staleTime: 30_000 });
  const canWrite = session.data?.admin?.permissions.includes('ops.application.write') ?? false;
  const applications = useQuery({
    queryKey: [
      'ops-applications', page, size, filters.appId, filters.name,
      filters.merchantId, filters.status, filters.unavailable
    ],
    queryFn: () => getApplications({ page, size, ...filters })
  });
  const summary = useQuery({
    queryKey: ['ops-applications-summary'],
    queryFn: getApplicationSummary,
    staleTime: 5 * 60_000
  });
  const detail = useQuery({
    queryKey: ['ops-application', selectedId],
    queryFn: () => getApplication(selectedId!),
    enabled: Boolean(selectedId)
  });
  const initialMerchant = useQuery({
    queryKey: ['ops-merchant', initialMerchantId],
    queryFn: () => getMerchant(initialMerchantId!),
    enabled: Boolean(initialMerchantId)
  });
  const merchantLookup = useQuery({
    queryKey: ['ops-merchant-options', merchantSearch],
    queryFn: () => getMerchants({
      page: 0,
      size: 20,
      ...(merchantSearch.trim().toUpperCase().startsWith('M')
        ? { merchantNo: merchantSearch.trim() }
        : { name: merchantSearch.trim() || undefined })
    })
  });
  const merchantOptions = useMemo(() => {
    const merchants = [...(merchantLookup.data?.items ?? [])];
    if (initialMerchant.data && !merchants.some(
      (item) => item.merchantId === initialMerchant.data?.merchantId
    )) {
      merchants.unshift(initialMerchant.data);
    }
    return merchants.map(merchantOption);
  }, [initialMerchant.data, merchantLookup.data?.items]);
  const selectedFormMerchant = merchantOptions.find(
    (option) => option.value === formMerchantId
  )?.merchant;

  useEffect(() => {
    if (!formOpen) return;
    form.setFieldsValue(editing ? {
      merchantId: editing.merchantId,
      name: editing.name,
      status: editing.status
    } : {
      merchantId: filters.merchantId,
      status: 'ACTIVE'
    });
  }, [editing, filters.merchantId, form, formOpen]);

  useEffect(() => {
    if (formOpen && !editing && selectedFormMerchant?.status === 'DISABLED') {
      form.setFieldValue('status', 'DISABLED');
    }
  }, [editing, form, formOpen, selectedFormMerchant?.status]);

  const refreshApplicationData = async (applicationId?: string) => {
    await queryClient.invalidateQueries({ queryKey: ['ops-applications'] });
    await queryClient.invalidateQueries({ queryKey: ['ops-applications-summary'] });
    await queryClient.invalidateQueries({ queryKey: ['ops-merchants'] });
    if (applicationId) {
      await queryClient.invalidateQueries({ queryKey: ['ops-application', applicationId] });
    }
  };

  const save = useMutation({
    mutationFn: (values: ApplicationFormValues) => editing
      ? updateApplication(editing, values.name, createIdempotencyKey())
      : createApplication(values, createIdempotencyKey()),
    onSuccess: async (application) => {
      setFormOpen(false);
      form.resetFields();
      await refreshApplicationData(application.applicationId);
      void message.success(editing ? '应用名称已更新' : '应用已创建');
    },
    onError: (error) => void message.error(applicationError(error))
  });

  const changeStatus = useMutation({
    mutationFn: ({ application, status }: {
      application: OpsApplication;
      status: ApplicationStatus;
    }) => changeApplicationStatus(application, status, createIdempotencyKey()),
    onSuccess: async (application) => {
      await refreshApplicationData(application.applicationId);
      void message.success(application.status === 'ACTIVE' ? '应用已启用' : '应用已停用');
    },
    onError: (error) => void message.error(applicationError(error))
  });

  const remove = useMutation({
    mutationFn: (application: OpsApplication) =>
      deleteApplication(application, createIdempotencyKey()),
    onSuccess: async () => {
      setSelectedId(undefined);
      await refreshApplicationData();
      void message.success('应用已删除');
    },
    onError: (error) => void message.error(applicationError(error))
  });

  const batchChangeStatus = useMutation({
    mutationFn: async ({ apps, target }: {
      apps: OpsApplication[];
      target: ApplicationStatus;
    }) => {
      const targets = apps.filter((app) => app.status !== target);
      const skipped = apps.length - targets.length;
      const results = await Promise.allSettled(
        targets.map((app) => changeApplicationStatus(app, target, createIdempotencyKey()))
      );
      let success = 0;
      const failures: { name: string; reason: string }[] = [];
      results.forEach((result, index) => {
        if (result.status === 'fulfilled') {
          success += 1;
        } else {
          failures.push({ name: targets[index]?.name ?? '', reason: applicationError(result.reason) });
        }
      });
      return { success, skipped, failures };
    },
    onSuccess: async ({ success, skipped, failures }) => {
      setSelectedApps([]);
      await refreshApplicationData();
      if (failures.length === 0) {
        void message.success(`批量操作完成：成功 ${success} 项${skipped > 0 ? `，跳过 ${skipped} 项` : ''}`);
      } else {
        void message.warning(`成功 ${success} 项，失败 ${failures.length} 项：${
          failures.map((item) => `${item.name}（${item.reason}）`).join('；')
        }`);
      }
    },
    onError: (error) => void message.error(applicationError(error))
  });

  const handleExport = async () => {
    setExporting(true);
    try {
      const { items, total, truncated } = await fetchAllForExport({ page: 0, size: 100, ...filters });
      exportApplicationsCsv(items, { total, truncated });
    } catch {
      void message.error('导出失败，请稍后重试。');
    } finally {
      setExporting(false);
    }
  };

  const confirmStatusChange = (application: OpsApplication) => {
    const target: ApplicationStatus = application.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    modal.confirm({
      title: target === 'ACTIVE' ? '确认启用应用？' : '确认停用应用？',
      content: target === 'ACTIVE'
        ? '启用后，应用可在所属商户启用时发起业务请求。'
        : '停用后，该应用将不能发起新的支付业务。',
      okText: target === 'ACTIVE' ? '启用' : '停用',
      okButtonProps: { danger: target === 'DISABLED' },
      onOk: async () => { await changeStatus.mutateAsync({ application, status: target }); }
    });
  };

  const confirmDelete = (application: OpsApplication) => {
    modal.confirm({
      title: '确认删除应用？',
      content: `将永久删除“${application.name}”。只有已停用且没有业务依赖的应用可以删除。`,
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: async () => { await remove.mutateAsync(application); }
    });
  };

  const confirmBatchStatus = (target: ApplicationStatus) => {
    const targets = selectedApps.filter((app) => app.status !== target);
    const names = targets.slice(0, 5).map((app) => app.name);
    const label = target === 'ACTIVE' ? '启用' : '停用';
    modal.confirm({
      title: `确认批量${label} ${targets.length} 个应用？`,
      content: targets.length > 5
        ? `“${names.join('、')}”等 ${targets.length} 个应用将被${label}。`
        : `“${names.join('、')}”将被${label}。`,
      okText: label,
      okButtonProps: { danger: target === 'DISABLED' },
      onOk: async () => { await batchChangeStatus.mutateAsync({ apps: selectedApps, target }); }
    });
  };

  const quickFilter = (next: Filters) => {
    setDraftFilters(next);
    setFilters(next);
    setPage(0);
  };

  const columns = useMemo<TableColumnsType<OpsApplication>>(() => [
    {
      title: 'MiniPay AppID', dataIndex: 'appId', width: 190,
      render: (value: string) => <Typography.Text copyable ellipsis={{ tooltip: value }}>{value}</Typography.Text>
    },
    {
      title: '应用名称', dataIndex: 'name', width: 140,
      render: (value: string) => <Typography.Text ellipsis={{ tooltip: value }}>{value}</Typography.Text>
    },
    {
      title: '所属商户', key: 'merchant', width: 170,
      render: (_, application) => (
        <div className={styles.merchantCell}>
          <Button
            type="link"
            className={styles.merchantLink}
            onClick={() => navigate(`/merchants?merchantId=${application.merchantId}`)}
          >
            <Typography.Text ellipsis={{ tooltip: application.merchantName }}>
              {application.merchantName}
            </Typography.Text>
          </Button>
          <Typography.Text type="secondary">{application.merchantNo}</Typography.Text>
        </div>
      )
    },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (status: ApplicationStatus) => (
        status === 'ACTIVE' ? <Tag color="green">已启用</Tag> : <Tag>已停用</Tag>
      )
    },
    {
      title: '可用性', key: 'availability', width: 90,
      render: (_, application) => {
        const info = availabilityInfo(application);
        return info.reasons.length === 0
          ? <Tag color="green">可用</Tag>
          : <Tooltip title={info.reasons.join('；')}><Tag color={info.color}>不可用</Tag></Tooltip>;
      }
    },
    {
      title: '近30天交易', key: 'recentTransactionCount', width: 110,
      render: (_, application) => (
        <Typography.Text>{application.recentTransactionCount
          ? application.recentTransactionCount.toLocaleString()
          : '-'}</Typography.Text>
      )
    },
    {
      title: '创建时间', dataIndex: 'createdAt', width: 150,
      render: (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm:ss')
    },
    {
      title: '更新时间', dataIndex: 'updatedAt', width: 150, responsive: ['xl'],
      render: (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm:ss')
    },
    {
      title: '操作', key: 'actions', width: 230,
      render: (_, application) => {
        const cannotEnable = application.status === 'DISABLED'
          && application.merchantStatus !== 'ACTIVE';
        return (
          <Space className={styles.actions} size={[2, 0]} wrap>
            <Button type="link" onClick={() => setSelectedId(application.applicationId)}>详情</Button>
            {canWrite ? <Button type="link" onClick={() => {
              setEditing(application);
              setFormOpen(true);
            }}>编辑</Button> : null}
            {canWrite ? (
              <Tooltip title={cannotEnable ? '所属商户已停用，不能启用应用' : undefined}>
                <span><Button
                  type="link"
                  danger={application.status === 'ACTIVE'}
                  disabled={cannotEnable}
                  onClick={() => confirmStatusChange(application)}
                >{application.status === 'ACTIVE' ? '停用' : '启用'}</Button></span>
              </Tooltip>
            ) : null}
            {canWrite ? (
              <Tooltip title={application.deletable ? undefined : blockedReasonLabels[
                application.deletionBlockedReason ?? 'APPLICATION_HAS_DEPENDENCIES'
              ]}>
                <span><Button
                  type="link"
                  danger
                  disabled={!application.deletable}
                  onClick={() => confirmDelete(application)}
                >删除</Button></span>
              </Tooltip>
            ) : null}
          </Space>
        );
      }
    }
  ], [canWrite, navigate]);

  const applyFilters = () => {
    setPage(0);
    setFilters({ ...draftFilters });
  };

  return (
    <Card className={styles.card}>
      <Flex className={styles.header} justify="space-between" align="flex-start" gap={16} wrap>
        <div>
          <Typography.Title level={3}>应用管理</Typography.Title>
          <Typography.Paragraph type="secondary">
            管理全平台商户应用；MiniPay AppID 由系统生成且不可修改。
          </Typography.Paragraph>
        </div>
        <Space>
          <Button loading={exporting} onClick={() => void handleExport()}>导出 CSV</Button>
          {canWrite ? <Button type="primary" onClick={() => {
            setEditing(undefined);
            setFormOpen(true);
          }}>新建应用</Button> : null}
        </Space>
      </Flex>

      {summary.data ? (
        <Flex className={styles.summaryCards} gap={12} wrap>
          <Card className={styles.summaryCard} onClick={() => quickFilter({})}>
            <Statistic title="应用总数" value={summary.data.totalCount} />
          </Card>
          <Card
            className={styles.summaryCard}
            onClick={() => quickFilter({ status: 'ACTIVE' })}
          >
            <Statistic
              title="已启用"
              value={summary.data.activeCount}
              suffix={summary.data.totalCount > 0
                ? <span className={styles.summaryPct}>
                    {(summary.data.activeCount / summary.data.totalCount * 100).toFixed(1)}%
                  </span>
                : undefined}
            />
          </Card>
          <Card
            className={styles.summaryCard}
            onClick={() => quickFilter({ status: 'DISABLED' })}
          >
            <Statistic
              title="已停用"
              value={summary.data.disabledCount}
              suffix={summary.data.totalCount > 0
                ? <span className={styles.summaryPct}>
                    {(summary.data.disabledCount / summary.data.totalCount * 100).toFixed(1)}%
                  </span>
                : undefined}
            />
          </Card>
          <Card
            className={styles.summaryCard}
            onClick={() => quickFilter({ unavailable: true })}
          >
            <Statistic
              title="不可用"
              value={summary.data.unavailableCount}
              suffix={summary.data.totalCount > 0
                ? <span className={styles.summaryPct}>
                    {(summary.data.unavailableCount / summary.data.totalCount * 100).toFixed(1)}%
                  </span>
                : undefined}
            />
          </Card>
        </Flex>
      ) : null}

      <Flex className={styles.filters} gap={12} wrap>
        <Input
          allowClear
          placeholder="输入 MiniPay AppID"
          value={draftFilters.appId}
          onChange={(event) => setDraftFilters((value) => ({ ...value, appId: event.target.value }))}
          onPressEnter={applyFilters}
        />
        <Input
          allowClear
          placeholder="输入应用名称"
          value={draftFilters.name}
          onChange={(event) => setDraftFilters((value) => ({ ...value, name: event.target.value }))}
          onPressEnter={applyFilters}
        />
        <Select
          showSearch
          allowClear
          filterOption={false}
          placeholder="选择所属商户"
          value={draftFilters.merchantId}
          options={merchantOptions}
          loading={merchantLookup.isFetching}
          onSearch={setMerchantSearch}
          onChange={(merchantId) => setDraftFilters((value) => ({ ...value, merchantId }))}
        />
        <Select
          allowClear
          placeholder="全部状态"
          value={draftFilters.status}
          options={[{ label: '已启用', value: 'ACTIVE' }, { label: '已停用', value: 'DISABLED' }]}
          onChange={(status) => setDraftFilters((value) => ({
            ...value,
            status,
            ...(status ? { unavailable: undefined } : {})
          }))}
        />
        <Button type="primary" onClick={applyFilters}>查询</Button>
        <Button onClick={() => {
          setDraftFilters({});
          setFilters({});
          setMerchantSearch('');
          setPage(0);
          if (location.search) navigate('/applications', { replace: true });
        }}>重置</Button>
      </Flex>

      {applications.isError ? (
        <Alert
          showIcon
          type="error"
          title="应用列表加载失败"
          action={<Button onClick={() => void applications.refetch()}>重试</Button>}
        />
      ) : (
        <>
          {selectedApps.length > 0 && canWrite ? (
            <Flex className={styles.batchBar} justify="space-between" align="center" wrap>
              <Typography.Text>已选择 {selectedApps.length} 项</Typography.Text>
              <Space>
                <Button
                  disabled={!selectedApps.some((app) => app.status === 'DISABLED')}
                  onClick={() => confirmBatchStatus('ACTIVE')}
                >批量启用</Button>
                <Button
                  disabled={!selectedApps.some((app) => app.status === 'ACTIVE')}
                  onClick={() => confirmBatchStatus('DISABLED')}
                >批量停用</Button>
                <Button type="link" onClick={() => setSelectedApps([])}>取消选择</Button>
              </Space>
            </Flex>
          ) : null}
          <Table<OpsApplication>
            className={styles.table}
            rowKey="applicationId"
            columns={columns}
            dataSource={applications.data?.items ?? []}
            loading={applications.isPending || applications.isFetching}
            tableLayout="fixed"
            locale={{ emptyText: <Empty description="没有符合条件的应用" /> }}
            rowSelection={canWrite ? {
              selectedRowKeys: selectedApps.map((app) => app.applicationId),
              onChange: (_keys, rows) => setSelectedApps(rows)
            } : undefined}
            pagination={{
              current: page + 1,
              pageSize: size,
              total: applications.data?.total ?? 0,
              showSizeChanger: true,
              pageSizeOptions: [10, 20, 50, 100],
              showTotal: (total) => `共 ${total} 条`,
              onChange: (nextPage, nextSize) => {
                setPage(nextPage - 1);
                setSize(nextSize);
                setSelectedApps([]);
              }
            }}
          />
        </>
      )}

      <Drawer
        open={formOpen}
        title={editing ? '编辑应用' : '新建应用'}
        size={640}
        destroyOnHidden
        onClose={() => { setFormOpen(false); form.resetFields(); }}
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
          <Row gutter={16}>
            <Col span={24}>
              {editing ? (
                <Descriptions column={1} size="small" bordered items={[
                  { key: 'appId', label: 'MiniPay AppID', children: editing.appId },
                  { key: 'merchant', label: '所属商户', children: `${editing.merchantName}（${editing.merchantNo}）` },
                  { key: 'status', label: '当前状态', children: editing.status === 'ACTIVE' ? '已启用' : '已停用' }
                ]} />
              ) : (
                <Form.Item label="所属商户" name="merchantId" rules={[
                  { required: true, message: '请选择所属商户' }
                ]}>
                  <Select
                    showSearch
                    filterOption={false}
                    placeholder="输入商户名称或商户号搜索"
                    options={merchantOptions}
                    loading={merchantLookup.isFetching}
                    onSearch={setMerchantSearch}
                    onChange={(merchantId) => {
                      const merchant = merchantOptions.find((item) => item.value === merchantId)?.merchant;
                      if (merchant?.status === 'DISABLED') form.setFieldValue('status', 'DISABLED');
                    }}
                  />
                </Form.Item>
              )}
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label="应用名称" name="name" rules={[
                { required: true, message: '请输入应用名称' },
                { min: 2, max: 64, message: '应用名称长度为 2～64 个字符' }
              ]}>
                <Input maxLength={64} showCount placeholder="请输入应用名称" />
              </Form.Item>
            </Col>
            {!editing ? <Col xs={24} md={12}>
              <Form.Item
                label="初始状态"
                name="status"
                extra={selectedFormMerchant?.status === 'DISABLED'
                  ? '所属商户已停用，新应用只能保持停用。' : undefined}
              >
                <Select options={[
                  {
                    label: '已启用', value: 'ACTIVE',
                    disabled: selectedFormMerchant?.status === 'DISABLED'
                  },
                  { label: '已停用', value: 'DISABLED' }
                ]} />
              </Form.Item>
            </Col> : null}
          </Row>
        </Form>
      </Drawer>

      <Drawer
        title="应用详情"
        size={540}
        open={Boolean(selectedId)}
        onClose={() => setSelectedId(undefined)}
      >
        {detail.isError ? <Alert type="error" showIcon title="应用详情加载失败" /> : (
          <Descriptions column={1} bordered size="small" items={detail.data ? [
            { key: 'appId', label: 'MiniPay AppID', children: detail.data.appId },
            { key: 'name', label: '应用名称', children: detail.data.name },
            {
              key: 'merchant', label: '所属商户',
              children: (
                <Button type="link" className={styles.merchantLink} onClick={() => {
                  const merchantId = detail.data?.merchantId;
                  setSelectedId(undefined);
                  if (merchantId) navigate(`/merchants?merchantId=${merchantId}`);
                }}>
                  {detail.data.merchantName}（{detail.data.merchantNo}）
                </Button>
              )
            },
            { key: 'merchantStatus', label: '商户状态', children: merchantStatusLabel(detail.data.merchantStatus) },
            { key: 'status', label: '应用状态', children: detail.data.status === 'ACTIVE' ? '已启用' : '已停用' },
            { key: 'effective', label: '当前可用性', children: (() => {
              const info = availabilityInfo(detail.data!);
              return info.reasons.length === 0
                ? <Tag color="green">可用</Tag>
                : <Tooltip title={info.reasons.join('；')}><Tag color={info.color}>不可用</Tag></Tooltip>;
            })() },
            { key: 'transactions', label: '历史交易', children: detail.data.hasTransactions ? '存在' : '无' },
            { key: 'recentTx', label: '近30天交易', children: detail.data.recentTransactionCount
              ? `${detail.data.recentTransactionCount.toLocaleString()} 笔` : '无' },
            { key: 'lastTx', label: '最近交易时间', children: detail.data.lastTransactionAt
              ? dayjs(detail.data.lastTransactionAt).format('YYYY-MM-DD HH:mm:ss') : '无' },
            { key: 'createdAt', label: '创建时间', children: dayjs(detail.data.createdAt).format('YYYY-MM-DD HH:mm:ss') },
            { key: 'updatedAt', label: '更新时间', children: dayjs(detail.data.updatedAt).format('YYYY-MM-DD HH:mm:ss') },
            { key: 'delete', label: '删除条件', children: detail.data.deletable
              ? '允许删除'
              : blockedReasonLabels[detail.data.deletionBlockedReason ?? 'APPLICATION_HAS_DEPENDENCIES'] }
          ] : []} />
        )}
      </Drawer>
    </Card>
  );
}

export default function ApplicationsPage() {
  return <AuthGate routeKey="applications"><ApplicationsContent /></AuthGate>;
}
