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
  MerchantApplyStatus,
  MerchantType,
  OpsMerchantApply
} from '@minipay/api-contracts';
import { ApiProblemError, createIdempotencyKey } from '@minipay/api-client';
import dayjs from 'dayjs';
import { AuthGate } from '../components/AuthGate';
import { getSession } from '../services/auth';
import {
  approveMerchantApply,
  getMerchantApply,
  getMerchantApplies,
  rejectMerchantApply,
  requestSupplementMerchantApply
} from '../services/ops';
import styles from './review.module.less';

type StatusFilter = 'PENDING' | 'APPROVED' | 'SUPPLEMENT' | 'ALL';

const statusLabels: Record<MerchantApplyStatus, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  PENDING: { text: '待审核', color: 'orange' },
  APPROVED: { text: '已通过', color: 'green' },
  REJECTED: { text: '已驳回', color: 'red' },
  SUPPLEMENT: { text: '待补充', color: 'blue' }
};

const merchantTypeLabels: Record<MerchantType, string> = {
  PERSONAL: '个人经营',
  INDIVIDUAL: '个体工商',
  ENTERPRISE: '企业'
};

function errorMessage(error: unknown): string {
  if (error instanceof ApiProblemError) {
    if (error.problem.code === 'MERCHANT_APPLY_VERSION_CONFLICT') return '该申请已被其他运营人员处理，请刷新后重试。';
    if (error.problem.code === 'MERCHANT_APPLY_NOT_PENDING') return '该申请已不在待审核状态。';
    return error.problem.detail ?? error.problem.title;
  }
  return '操作失败，请稍后重试。';
}

interface ReviewFormValues {
  action: 'approve' | 'reject' | 'supplement';
  reason?: string;
}

function ReviewContent() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('PENDING');
  const [selectedId, setSelectedId] = useState<number>();
  const [reviewTarget, setReviewTarget] = useState<OpsMerchantApply>();
  const [reviewForm] = Form.useForm<ReviewFormValues>();
  const session = useQuery({ queryKey: ['session'], queryFn: getSession, staleTime: 30_000 });
  const canWrite = session.data?.admin?.permissions.includes('ops.merchant.write') ?? false;

  const applies = useQuery({
    queryKey: ['ops-merchant-applies', page, size, statusFilter],
    queryFn: () => getMerchantApplies({
      page,
      size,
      applyStatus: statusFilter === 'ALL' ? undefined : statusFilter
    })
  });
  const detail = useQuery({
    queryKey: ['ops-merchant-apply', selectedId],
    queryFn: () => getMerchantApply(selectedId!),
    enabled: selectedId !== undefined
  });

  const refreshApplies = async () => {
    await queryClient.invalidateQueries({ queryKey: ['ops-merchant-applies'] });
    await queryClient.invalidateQueries({ queryKey: ['ops-merchants'] });
  };

  const review = useMutation({
    mutationFn: ({ apply, values }: { apply: OpsMerchantApply; values: ReviewFormValues }) => {
      const key = createIdempotencyKey();
      if (values.action === 'approve') return approveMerchantApply(apply, key);
      if (values.action === 'reject') return rejectMerchantApply(apply, values.reason!, key);
      return requestSupplementMerchantApply(apply, values.reason!, key);
    },
    onSuccess: async (apply) => {
      setReviewTarget(undefined);
      reviewForm.resetFields();
      await refreshApplies();
      void message.success(apply.applyStatus === 'APPROVED'
        ? '已通过，商户已创建' : apply.applyStatus === 'REJECTED' ? '已驳回' : '已要求补充资料');
    },
    onError: (error) => void message.error(errorMessage(error))
  });

  const columns = useMemo<TableColumnsType<OpsMerchantApply>>(() => [
    { title: '申请 ID', dataIndex: 'id', width: 90 },
    { title: '申请人', dataIndex: 'contactName', width: 110 },
    { title: '手机号', dataIndex: 'contactMobile', width: 130 },
    { title: '店铺名称', dataIndex: 'shopName', width: 150 },
    {
      title: '商户类型', dataIndex: 'merchantType', width: 100,
      render: (type: MerchantType) => merchantTypeLabels[type] ?? type
    },
    {
      title: '申请状态', dataIndex: 'applyStatus', width: 100,
      render: (status: MerchantApplyStatus) => {
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
          <Typography.Title level={3}>入驻审核</Typography.Title>
          <Typography.Paragraph type="secondary">审核个人用户提交的商户入驻申请；通过后自动创建商户并绑定账户。</Typography.Paragraph>
        </div>
      </Flex>

      <Flex className={styles.tabs} gap={8} wrap>
        {([['PENDING', '待审核'], ['APPROVED', '已通过'], ['SUPPLEMENT', '待补充'], ['ALL', '全部']] as const).map(([key, label]) => (
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
        <Table<OpsMerchantApply>
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
            { key: 'shopName', label: '店铺名称', children: detail.data.shopName },
            { key: 'merchantType', label: '商户类型', children: merchantTypeLabels[detail.data.merchantType] ?? '-' },
            { key: 'mccCode', label: '经营类目', children: detail.data.mccCode || '-' },
            { key: 'address', label: '经营地址', children: detail.data.address || '-' },
            {
              key: 'location', label: '经营位置', children: detail.data.latitude != null && detail.data.longitude != null
                ? `经度 ${detail.data.longitude}，纬度 ${detail.data.latitude}` : '-'
            },
            { key: 'shopImages', label: '店铺图片', children: detail.data.shopImages || '-' },
            { key: 'contactName', label: '联系人姓名', children: detail.data.contactName },
            { key: 'contactMobile', label: '联系人手机号', children: detail.data.contactMobile },
            { key: 'contactEmail', label: '联系人邮箱', children: detail.data.contactEmail || '-' },
            { key: 'remark', label: '备注', children: detail.data.remark || '-' },
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
            { key: 'resultantMerchantId', label: '生成商户', children: detail.data.resultantMerchantId || '-' }
          ] : []} />
        )}
      </Drawer>

      <Modal
        open={Boolean(reviewTarget)}
        title="审核入驻申请"
        okText="提交"
        okButtonProps={{ loading: review.isPending }}
        onOk={() => reviewTarget && reviewForm.submit()}
        onCancel={() => { setReviewTarget(undefined); reviewForm.resetFields(); }}
        destroyOnHidden
      >
        {reviewTarget ? (
          <>
            <Descriptions column={1} size="small" bordered items={[
              { key: 'shopName', label: '店铺名称', children: reviewTarget.shopName },
              { key: 'contactName', label: '申请人', children: reviewTarget.contactName },
              { key: 'contactMobile', label: '手机号', children: reviewTarget.contactMobile },
              { key: 'merchantType', label: '商户类型', children: merchantTypeLabels[reviewTarget.merchantType] ?? '-' },
              { key: 'address', label: '经营地址', children: reviewTarget.address || '-' },
              {
                key: 'location', label: '经营位置', children: reviewTarget.latitude != null && reviewTarget.longitude != null
                  ? `经度 ${reviewTarget.longitude}，纬度 ${reviewTarget.latitude}` : '-'
              }
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

export default function ReviewPage() {
  return <AuthGate routeKey="review"><ReviewContent /></AuthGate>;
}
