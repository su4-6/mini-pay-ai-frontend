import { useState } from 'react';
import {
  Alert, Button, Card, Descriptions, Drawer, Empty, Input, Select, Table, Tag, Typography
} from 'antd';
import type { TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { AuthGate } from '../components/AuthGate';
import { getOpsTransfer, getOpsTransfers } from '../services/ops';
import type { OpsTransfer, OpsTransferDetail } from '@minipay/api-contracts';
import styles from './orders.module.less';

const statusLabels: Record<string, { label: string; color: string }> = {
  PROCESSING: { label: '处理中', color: 'processing' },
  SUCCEEDED: { label: '成功', color: 'success' },
  FAILED: { label: '失败', color: 'error' }
};
const money = (cent = 0) => `¥ ${(cent / 100).toFixed(2)}`;

export default function TransfersPage() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [status, setStatus] = useState<string>();
  const [transferNo, setTransferNo] = useState('');
  const [detail, setDetail] = useState<OpsTransferDetail>();

  const transfers = useQuery({
    queryKey: ['ops-transfers', page, size, status, transferNo],
    queryFn: () =>
      getOpsTransfers({
        page,
        size,
        status,
        transferNo: transferNo.trim() || undefined
      })
  });

  const loadDetail = async (orderNo: string) => {
    try {
      setDetail(await getOpsTransfer(orderNo));
    } catch {
      setDetail(undefined);
    }
  };

  const columns: TableColumnsType<OpsTransfer> = [
    { title: '转账单号', dataIndex: 'transferNo', width: 190, ellipsis: true },
    { title: '付款方', dataIndex: 'payerMasked', width: 130, render: (v?: string) => v || '—' },
    { title: '收款方', dataIndex: 'receiverMasked', width: 130, render: (v?: string) => v || '—' },
    { title: '金额', dataIndex: 'amountCent', width: 120, render: (v: number) => money(v) },
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
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 165,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm')
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right' as const,
      render: (_: unknown, row: OpsTransfer) => (
        <Button type="link" onClick={() => void loadDetail(row.transferNo)}>详情</Button>
      )
    }
  ];

  return (
    <AuthGate routeKey="transfers">
      <Card
        className={styles.card}
        title={
          <div>
            <Typography.Title level={3}>转账订单</Typography.Title>
            <Typography.Text type="secondary">全平台站内转账查询（只读）</Typography.Text>
          </div>
        }
        extra={<Button onClick={() => void transfers.refetch()}>刷新</Button>}
      >
        <div className={styles.filters}>
          <Input
            placeholder="转账单号"
            value={transferNo}
            onChange={(e) => setTransferNo(e.target.value)}
            style={{ width: 180 }}
            allowClear
          />
          <Select
            placeholder="状态"
            value={status}
            onChange={setStatus}
            allowClear
            options={Object.entries(statusLabels).map(([value, s]) => ({ value, label: s.label }))}
          />
          <Button type="primary" onClick={() => void transfers.refetch()}>查询</Button>
        </div>
        {transfers.isError ? (
          <Alert
            showIcon
            type="error"
            title="转账订单加载失败"
            description="请稍后重试；若问题持续，请使用请求标识联系管理员。"
            action={<Button onClick={() => void transfers.refetch()}>重试</Button>}
          />
        ) : (
          <Table<OpsTransfer>
            rowKey="transferNo"
            columns={columns}
            dataSource={transfers.data?.items ?? []}
            loading={transfers.isPending}
            scroll={{ x: 900 }}
            locale={{ emptyText: <Empty description="暂无转账订单" /> }}
            pagination={{
              current: page + 1,
              pageSize: size,
              total: transfers.data?.total ?? 0,
              showSizeChanger: true,
              pageSizeOptions: [10, 20, 50, 100],
              showTotal: (total) => `共 ${total} 条`,
              onChange: (next, nextSize) => {
                setPage(next - 1);
                setSize(nextSize);
              }
            }}
          />
        )}
      </Card>
      <Drawer width={480} title="转账订单详情" open={!!detail} onClose={() => setDetail(undefined)}>
        {detail && (
          <Descriptions
            column={1}
            bordered
            size="small"
            items={[
              { key: 'no', label: '转账单号', children: detail.transferNo },
              { key: 'payer', label: '付款方', children: detail.payerMasked ?? '—' },
              { key: 'receiver', label: '收款方', children: detail.receiverMasked ?? '—' },
              { key: 'amount', label: '金额', children: money(detail.amountCent) },
              {
                key: 'status',
                label: '状态',
                children: statusLabels[detail.status]?.label ?? detail.status
              },
              { key: 'failure', label: '失败原因', children: detail.failureCode ?? '—' },
              {
                key: 'created',
                label: '创建时间',
                children: dayjs(detail.createdAt).format('YYYY-MM-DD HH:mm:ss')
              },
              {
                key: 'updated',
                label: '更新时间',
                children: dayjs(detail.updatedAt).format('YYYY-MM-DD HH:mm:ss')
              }
            ]}
          />
        )}
      </Drawer>
    </AuthGate>
  );
}
