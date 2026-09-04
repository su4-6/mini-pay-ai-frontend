import { useState } from 'react';
import {
  Alert, App, Button, Card, Descriptions, Drawer, Empty, Popconfirm, Space, Table, Tag, Typography
} from 'antd';
import type { TableColumnsType } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { AuthGate } from '../components/AuthGate';
import {
  getOpsNotification, getOpsNotifications, retryNotification
} from '../services/ops';
import type { OpsNotification, OpsNotificationDetail } from '@minipay/api-contracts';
import styles from './orders.module.less';

const typeLabels: Record<string, string> = {
  PAYMENT: '支付通知',
  PAYMENT_SUCCEEDED: '支付成功通知',
  REFUND: '退款通知',
  REFUND_SUCCEEDED: '退款成功通知'
};
const statusLabels: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待投递', color: 'processing' },
  RETRYING: { label: '重试中', color: 'warning' },
  SUCCEEDED: { label: '已送达', color: 'success' },
  FAILED: { label: '失败', color: 'error' }
};

export default function NotificationsPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [size, setSize] = useState(20);
  const [detail, setDetail] = useState<OpsNotificationDetail>();

  const notifications = useQuery({
    queryKey: ['ops-notifications', size],
    queryFn: () => getOpsNotifications(size)
  });

  const loadDetail = async (notificationId: string) => {
    try {
      setDetail(await getOpsNotification(notificationId));
    } catch {
      setDetail(undefined);
    }
  };

  const retry = useMutation({
    mutationFn: (notificationId: string) => retryNotification(notificationId),
    onSuccess: async () => {
      void message.success('已发起人工重试');
      setDetail(undefined);
      await queryClient.invalidateQueries({ queryKey: ['ops-notifications'] });
    },
    onError: (error: Error) => {
      const detail = error.message?.trim();
      void message.error(detail || '人工重试失败，请稍后重试');
    }
  });

  const columns: TableColumnsType<OpsNotification> = [
    {
      title: '类型',
      dataIndex: 'type',
      width: 110,
      render: (v: string) => typeLabels[v] ?? v
    },
    { title: '事件 ID', dataIndex: 'eventId', width: 200, ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => {
        const s = statusLabels[v] ?? { label: v, color: 'default' };
        return <Tag color={s.color}>{s.label}</Tag>;
      }
    },
    {
      title: '已尝试',
      dataIndex: 'attempts',
      width: 90,
      render: (v: number) => `${v} 次`
    },
    { title: '响应摘要', dataIndex: 'responseSummary', ellipsis: true, render: (v?: string) => v || '—' },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 165,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm')
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right' as const,
      render: (_: unknown, row: OpsNotification) => (
        <Space>
          <Button type="link" onClick={() => void loadDetail(row.notificationId)}>详情</Button>
          {row.status === 'FAILED' && (
            <Popconfirm title="确认人工重试该通知？" onConfirm={() => retry.mutate(row.notificationId)}>
              <Button type="link">重试</Button>
            </Popconfirm>
          )}
        </Space>
      )
    }
  ];

  return (
    <AuthGate routeKey="notifications">
      <Card
        className={styles.card}
        title={
          <div>
            <Typography.Title level={3}>商户通知</Typography.Title>
            <Typography.Text type="secondary">平台回调商户应用的异步通知记录（只读）</Typography.Text>
          </div>
        }
        extra={
          <Space>
            <Button onClick={() => void notifications.refetch()}>刷新</Button>
          </Space>
        }
      >
        {notifications.isError ? (
          <Alert
            showIcon
            type="error"
            title="通知记录加载失败"
            description="请稍后重试；若问题持续，请使用请求标识联系管理员。"
            action={<Button onClick={() => void notifications.refetch()}>重试</Button>}
          />
        ) : (
          <Table<OpsNotification>
            rowKey="notificationId"
            columns={columns}
            dataSource={notifications.data ?? []}
            loading={notifications.isPending}
            scroll={{ x: 1000 }}
            locale={{ emptyText: <Empty description="暂无通知记录" /> }}
            pagination={{
              pageSize: size,
              total: notifications.data?.length ?? 0,
              showSizeChanger: true,
              pageSizeOptions: [10, 20, 50, 100],
              onChange: (_next, nextSize) => setSize(nextSize)
            }}
          />
        )}
      </Card>
      <Drawer width={560} title="商户通知详情" open={!!detail} onClose={() => setDetail(undefined)}>
        {detail && (
          <>
            <Descriptions
              column={1}
              bordered
              size="small"
              items={[
                { key: 'type', label: '类型', children: typeLabels[detail.type] ?? detail.type },
                { key: 'eventId', label: '事件 ID', children: detail.eventId },
                {
                  key: 'status',
                  label: '状态',
                  children: statusLabels[detail.status]?.label ?? detail.status
                },
                { key: 'attempts', label: '已尝试', children: `${detail.attempts} 次` },
                { key: 'request', label: '请求摘要', children: detail.requestSummary ?? '—' },
                { key: 'response', label: '响应摘要', children: detail.responseSummary ?? '—' },
                {
                  key: 'created',
                  label: '创建时间',
                  children: dayjs(detail.createdAt).format('YYYY-MM-DD HH:mm:ss')
                }
              ]}
            />
            <Typography.Title level={5} style={{ marginTop: 20 }}>
              投递历史
            </Typography.Title>
            <Table
              rowKey="attemptNo"
              size="small"
              pagination={false}
              dataSource={detail.history ?? []}
              columns={[
                {
                  title: '序号',
                  dataIndex: 'attemptNo',
                  width: 70,
                  render: (v: number) => `#${v}`
                },
                {
                  title: '自动/手动',
                  dataIndex: 'automated',
                  width: 100,
                  render: (v: boolean) => (v ? '自动' : '手动')
                },
                { title: 'HTTP', dataIndex: 'httpStatus', width: 70, render: (v?: number) => v ?? '—' },
                { title: '结果', dataIndex: 'result', ellipsis: true },
                {
                  title: '时间',
                  dataIndex: 'occurredAt',
                  width: 165,
                  render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm:ss')
                }
              ]}
            />
            {detail.status === 'FAILED' && (
              <Popconfirm
                title="确认人工重试该通知？"
                onConfirm={() => retry.mutate(detail.notificationId)}
              >
                <Button type="primary" danger style={{ marginTop: 16 }}>
                  人工重试
                </Button>
              </Popconfirm>
            )}
          </>
        )}
      </Drawer>
    </AuthGate>
  );
}
