import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TableColumnsType } from 'antd';
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Flex,
  Form,
  Input,
  Modal,
  Radio,
  Space,
  Table,
  Tag,
  Typography
} from 'antd';
import type {
  ApplicationApplyStatus,
  OpsApplicationApply
} from '@minipay/api-contracts';
import { ApiProblemError, createIdempotencyKey } from '@minipay/api-client';
import dayjs from 'dayjs';
import { AuthGate } from '../components/AuthGate';
import { getSession } from '../services/auth';
import {
  approveApplicationApply,
  getApplicationApply,
  getApplicationApplies,
  rejectApplicationApply,
  requestSupplementApplicationApply
} from '../services/ops';
import styles from './application-review.module.less';

type StatusFilter = 'PENDING' | 'APPROVED' | 'REJECTED' | 'ALL';

const statusLabels: Record<ApplicationApplyStatus, { text: string; color: string }> = {
  PENDING: { text: '待审核', color: 'orange' },
  APPROVED: { text: '已通过', color: 'green' },
  REJECTED: { text: '已驳回', color: 'red' },
  SUPPLEMENT: { text: '待补充', color: 'blue' }
};

function errorMessage(error: unknown): string {
  if (error instanceof ApiProblemError) {
    if (error.problem.code === 'APPLICATION_APPLY_VERSION_CONFLICT') return '该申请已被其他运营人员处理，请刷新后重试。';
    if (error.problem.code === 'APPLICATION_APPLY_NOT_PENDING') return '该申请已不在待审核状态。';
    if (error.problem.code === 'APPLICATION_APPLY_NAME_CONFLICT') return '该商户下已存在同名应用。';
    if (error.problem.code === 'MERCHANT_NOT_ACTIVE') return '所属商户当前不可用，无法审核通过。';
    return error.problem.detail ?? error.problem.title;
  }
  return '操作失败，请稍后重试。';
}

interface ReviewFormValues {
  action: 'approve' | 'reject' | 'supplement';
  reason?: string;
}

function ApplicationReviewContent() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('PENDING');
  const [selectedId, setSelectedId] = useState<number>();
  const [reviewTarget, setReviewTarget] = useState<OpsApplicationApply>();
  const [reviewForm] = Form.useForm<ReviewFormValues>();
  const session = useQuery({ queryKey: ['session'], queryFn: getSession, staleTime: 30_000 });
  const canWrite = session.data?.admin?.permissions.includes('ops.application.write') ?? false;

  const applies = useQuery({
    queryKey: ['ops-application-applies', page, size, statusFilter],
    queryFn: () => getApplicationApplies({
      page,
      size,
      applyStatus: statusFilter === 'ALL' ? undefined : statusFilter
    })
  });
  const detail = useQuery({
    queryKey: ['ops-application-apply', selectedId],
    queryFn: () => getApplicationApply(selectedId!),
    enabled: selectedId !== undefined
  });

  const refreshApplies = async () => {
    await queryClient.invalidateQueries({ queryKey: ['ops-application-applies'] });
    await queryClient.invalidateQueries({ queryKey: ['ops-applications'] });
  };

  const review = useMutation({
    mutationFn: ({ apply, values }: { apply: OpsApplicationApply; values: ReviewFormValues }) => {
      const key = createIdempotencyKey();
      if (values.action === 'approve') return approveApplicationApply(apply, key);
      if (values.action === 'reject') return rejectApplicationApply(apply, values.reason!, key);
      return requestSupplementApplicationApply(apply, values.reason!, key);
    },
    onSuccess: async (apply) => {
      setReviewTarget(undefined);
      reviewForm.resetFields();
      await refreshApplies();
      void message.success(apply.applyStatus === 'APPROVED'
        ? '已通过，应用已创建' : apply.applyStatus === 'REJECTED' ? '已驳回' : '已要求补充资料');
    },
    onError: (error) => void message.error(errorMessage(error))
  });

  const columns = useMemo<TableColumnsType<OpsApplicationApply>>(() => [
    { title: '申请 ID', dataIndex: 'id', width: 90 },
    {
      title: '应用名称', dataIndex: 'name', width: 150,
      render: (value: string) => <Typography.Text ellipsis={{ tooltip: value }}>{value}</Typography.Text>
    },
    {
      title: '所属商户', key: 'merchant', width: 170,
      render: (_, apply) => (
        <Space size={2} direction="vertical">
          <Typography.Text ellipsis={{ tooltip: apply.merchantName ?? undefined }}>
            {apply.merchantName || '-'}
          </Typography.Text>
          <Typography.Text type="secondary">{apply.merchantNo || '-'}</Typography.Text>
        </Space>
      )
    },
    {
      title: '申请人', dataIndex: 'userId', width: 200,
      render: (value: string) => <Typography.Text ellipsis={{ tooltip: value }}>{value}</Typography.Text>
    },
    {
      title: '申请状态', dataIndex: 'applyStatus', width: 100,
      render: (status: ApplicationApplyStatus) => {
        const label = statusLabels[status];
        return <Tag color={label.color}>{label.text}</Tag>;
      }
    },
    { title: '申请时间', dataIndex: 'applyTime', width: 150, render: (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm:ss') },
    {
      title: '操作', key: 'actions', width: 160,
      render: (_, apply) => (
        <Space size={[2, 0]} wrap>
          <Button type="link" onClick={() => setSelectedId(apply.id)}>详情</Button>
          {canWrite && apply.applyStatus === 'PENDING' ? (
            <Button
              type="link"
              onClick={() => { setReviewTarget(apply); reviewForm.setFieldsValue({ action: 'approve' }); }}
            >审核</Button>
          ) : null}
        </Space>
      )
    }
  ], [canWrite, reviewForm]);

  return (
    <Card className={styles.card}>
      <Flex className={styles.header} justify="space-between" align="flex-start" gap={16} wrap>
        <div>
          <Typography.Title level={3}>应用审核</Typography.Title>
          <Typography.Paragraph type="secondary">审核商户提交的应用创建申请；通过后自动创建 MiniPay AppID 并生效。</Typography.Paragraph>
        </div>
      </Flex>

      <Flex className={styles.tabs} gap={8} wrap>
        {([['PENDING', '待审核'], ['APPROVED', '已通过'], ['REJECTED', '已驳回'], ['ALL', '全部']] as const).map(([key, label]) => (
          <Button
            key={key}
            size="small"
            type={statusFilter === key ? 'primary' : 'default'}
            onClick={() => { setStatusFilter(key); setPage(0); }}
          >{label}</Button>
        ))}
      </Flex>

      {applies.isError ? (
        <Alert showIcon type="error" title="审核列表加载失败" action={<Button onClick={() => void applies.refetch()}>重试</Button>} />
      ) : (
        <Table<OpsApplicationApply>
          className={styles.table}
          rowKey="id"
          columns={columns}
          dataSource={applies.data?.items ?? []}
          loading={applies.isPending || applies.isFetching}
          tableLayout="fixed"
          locale={{ emptyText: <Empty description="没有符合条件的申请" /> }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: applies.data?.total ?? 0,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50, 100],
            showTotal: (total) => `共 ${total} 条`,
            onChange: (nextPage, nextSize) => { setPage(nextPage - 1); setSize(nextSize); }
          }}
        />
      )}

      <Drawer title="申请详情" size={520} open={selectedId !== undefined} onClose={() => setSelectedId(undefined)}>
        {detail.isError ? <Alert type="error" showIcon title="申请详情加载失败" /> : (
          <Descriptions column={1} bordered size="small" items={detail.data ? [
            { key: 'id', label: '申请 ID', children: detail.data.id },
            { key: 'name', label: '应用名称', children: detail.data.name },
            { key: 'merchant', label: '所属商户', children: `${detail.data.merchantName || '-'}（${detail.data.merchantNo || '-'}）` },
            { key: 'userId', label: '申请人', children: detail.data.userId },
            {
              key: 'applyStatus', label: '申请状态', children: (() => {
                const label = statusLabels[detail.data.applyStatus];
                return (
                  <Space size={8}>
                    <Tag color={label.color}>{label.text}</Tag>
                    {detail.data.rejectReason ? `（${detail.data.rejectReason}）` : null}
                  </Space>
                );
              })()
            },
            { key: 'applyTime', label: '申请时间', children: dayjs(detail.data.applyTime).format('YYYY-MM-DD HH:mm:ss') },
            { key: 'auditTime', label: '审核时间', children: detail.data.auditTime ? dayjs(detail.data.auditTime).format('YYYY-MM-DD HH:mm:ss') : '-' },
            { key: 'auditAdminId', label: '审核人', children: detail.data.auditAdminId || '-' },
            { key: 'resultantApplicationId', label: '生成应用', children: detail.data.resultantApplicationId || '-' }
          ] : []} />
        )}
      </Drawer>

      <Modal
        open={Boolean(reviewTarget)}
        title="审核应用申请"
        okText="提交"
        okButtonProps={{ loading: review.isPending }}
        onOk={() => reviewTarget && reviewForm.submit()}
        onCancel={() => { setReviewTarget(undefined); reviewForm.resetFields(); }}
        destroyOnHidden
      >
        {reviewTarget ? (
          <>
            <Descriptions column={1} size="small" bordered items={[
              { key: 'name', label: '应用名称', children: reviewTarget.name },
              { key: 'merchant', label: '所属商户', children: `${reviewTarget.merchantName || '-'}（${reviewTarget.merchantNo || '-'}）` },
              { key: 'userId', label: '申请人', children: reviewTarget.userId }
            ]} />
            <Form
              form={reviewForm}
              layout="vertical"
              className={styles.reviewForm}
              onFinish={(values) => review.mutate({ apply: reviewTarget, values })}
            >
              <Form.Item label="审核操作" name="action" rules={[{ required: true }]}>
                <Radio.Group
                  onChange={(event) => {
                    const action = event.target.value as ReviewFormValues['action'];
                    if (action !== 'approve') reviewForm.setFieldsValue({ reason: '' });
                  }}
                >
                  <Radio value="approve">审核通过</Radio>
                  <Radio value="reject">驳回</Radio>
                  <Radio value="supplement">要求补充资料</Radio>
                </Radio.Group>
              </Form.Item>
              <Form.Item noStyle shouldUpdate={(prev, next) => prev.action !== next.action}>
                {({ getFieldValue }) => getFieldValue('action') === 'approve' ? null : (
                  <Form.Item
                    label={getFieldValue('action') === 'reject' ? '驳回原因' : '补充要求'}
                    name="reason"
                    rules={[{ required: true, message: '请输入原因' }, { max: 200, message: '原因不能超过 200 个字符' }]}
                  >
                    <Input.TextArea rows={3} maxLength={200} showCount placeholder="请输入原因（必填，最多 200 字）" />
                  </Form.Item>
                )}
              </Form.Item>
            </Form>
          </>
        ) : null}
      </Modal>
    </Card>
  );
}

export default function ApplicationReviewPage() {
  return <AuthGate routeKey="application-review"><ApplicationReviewContent /></AuthGate>;
}
