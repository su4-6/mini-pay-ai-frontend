import { useState } from 'react';
import {
  Alert, Button, Card, Descriptions, Drawer, Empty, Input, Select, Table, Tag, Typography
} from 'antd';
import type { TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { AuthGate } from '../components/AuthGate';
import { getOpsRefund, getOpsRefunds } from '../services/ops';
import type { OpsRefund, OpsRefundDetail } from '@minipay/api-contracts';
import styles from './orders.module.less';

const statusLabels: Record<string, { label: string; color: string }> = {
  PROCESSING: { label: '处理中', color: 'processing' },
  SUCCEEDED: { label: '成功', color: 'success' },
  FAILED: { label: '失败', color: 'error' }
};
const money = (cent = 0) => `¥ ${(cent / 100).toFixed(2)}`;

export default function RefundsPage() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [status, setStatus] = useState<string>();
  const [merchantNo, setMerchantNo] = useState('');
  const [detail, setDetail] = useState<OpsRefundDetail>();

  const refunds = useQuery({
    queryKey: ['ops-refunds', page, size, status, merchantNo],
    queryFn: () =>
      getOpsRefunds({
        page,
        size,
        status,
        merchantNo: merchantNo.trim() || undefined
      })
  });

  const loadDetail = async (refundOrderNo: string) => {
    try {
      setDetail(await getOpsRefund(refundOrderNo));
    } catch {
      setDetail(undefined);
    }
  };

  const columns: TableColumnsType<OpsRefund> = [
    { title: '退款单号', dataIndex: 'refundNo', width: 190, ellipsis: true },
    { title: '原支付订单号', dataIndex: 'paymentOrderNo', width: 190, ellipsis: true },
    {
      title: '商户',
      width: 160,
      ellipsis: true,
      render: (_: unknown, row: OpsRefund) =>
        row.merchantName ? `${row.merchantName}（${row.merchantNo}）` : '—'
    },
    { title: '退款金额', dataIndex: 'amountCent', width: 120, render: (v: number) => money(v) },
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
      render: (_: unknown, row: OpsRefund) => (
        <Button type="link" onClick={() => void loadDetail(row.refundNo)}>详情</Button>
      )
    }
  ];

  return (
    <AuthGate routeKey="refunds">
      <Card
        className={styles.card}
        title={
          <div>
            <Typography.Title level={3}>退款订单</Typography.Title>
            <Typography.Text type="secondary">全平台退款记录查询（只读）</Typography.Text>
          </div>
        }
        extra={<Button onClick={() => void refunds.refetch()}>刷新</Button>}
      >
        <div className={styles.filters}>
          <Input
            placeholder="商户号"
            value={merchantNo}
            onChange={(e) => setMerchantNo(e.target.value)}
            style={{ width: 160 }}
            allowClear
          />
          <Select
            placeholder="状态"
            value={status}
            onChange={setStatus}
            allowClear
            options={Object.entries(statusLabels).map(([value, s]) => ({ value, label: s.label }))}
          />
          <Button type="primary" onClick={() => void refunds.refetch()}>查询</Button>
        </div>
        {refunds.isError ? (
          <Alert
            showIcon
            type="error"
            title="退款订单加载失败"
            description="请稍后重试；若问题持续，请使用请求标识联系管理员。"
            action={<Button onClick={() => void refunds.refetch()}>重试</Button>}
          />
        ) : (
          <Table<OpsRefund>
            rowKey="refundNo"
            columns={columns}
            dataSource={refunds.data?.items ?? []}
            loading={refunds.isPending}
            scroll={{ x: 1000 }}
            locale={{ emptyText: <Empty description="暂无退款订单" /> }}
            pagination={{
              current: page + 1,
              pageSize: size,
              total: refunds.data?.total ?? 0,
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
      <Drawer width={520} title="退款订单详情" open={!!detail} onClose={() => setDetail(undefined)}>
        {detail && (
          <Descriptions
            column={1}
            bordered
            size="small"
            items={[
              { key: 'no', label: '退款单号', children: detail.refundNo },
              { key: 'payment', label: '原支付订单号', children: detail.paymentOrderNo },
              {
                key: 'merchant',
                label: '商户',
                children: detail.merchantName
                  ? `${detail.merchantName}（${detail.merchantNo}）`
                  : '—'
              },
              { key: 'amount', label: '退款金额', children: money(detail.amountCent) },
              {
                key: 'status',
                label: '状态',
                children: statusLabels[detail.status]?.label ?? detail.status
              },
              { key: 'reason', label: '退款原因', children: detail.reason ?? '—' },
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
