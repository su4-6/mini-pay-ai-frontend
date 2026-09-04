import { useState } from 'react';
import {
  Alert, Button, Card, Descriptions, Drawer, Empty, Input, Select, Table, Tag, Typography
} from 'antd';
import type { TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { AuthGate } from '../components/AuthGate';
import { getOpsPayment, getOpsPayments } from '../services/ops';
import type { OpsPaymentOrder, OpsPaymentOrderDetail } from '@minipay/api-contracts';
import styles from './orders.module.less';

const statusLabels: Record<string, { label: string; color: string }> = {
  PROCESSING: { label: '处理中', color: 'processing' },
  SUCCEEDED: { label: '成功', color: 'success' },
  FAILED: { label: '失败', color: 'error' }
};
const channelLabels: Record<string, string> = {
  WALLET: 'MiniPay 余额',
  WALLET_BALANCE: 'MiniPay 余额',
  ALIPAY: '支付宝沙箱',
  WECHAT: '微信沙箱',
  WECHAT_PAY: '微信沙箱'
};
const money = (cent = 0) => `¥ ${(cent / 100).toFixed(2)}`;

export default function PaymentsPage() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [status, setStatus] = useState<string>();
  const [merchantNo, setMerchantNo] = useState('');
  const [name, setName] = useState('');
  const [detail, setDetail] = useState<OpsPaymentOrderDetail>();

  const payments = useQuery({
    queryKey: ['ops-payments', page, size, status, merchantNo, name],
    queryFn: () =>
      getOpsPayments({
        page,
        size,
        status,
        merchantNo: merchantNo.trim() || undefined,
        name: name.trim() || undefined
      })
  });

  const loadDetail = async (paymentOrderNo: string) => {
    try {
      setDetail(await getOpsPayment(paymentOrderNo));
    } catch {
      setDetail(undefined);
    }
  };

  const columns: TableColumnsType<OpsPaymentOrder> = [
    { title: '支付订单号', dataIndex: 'paymentOrderNo', width: 190, ellipsis: true },
    {
      title: '商户',
      width: 160,
      ellipsis: true,
      render: (_: unknown, row: OpsPaymentOrder) =>
        row.merchantName ? `${row.merchantName}（${row.merchantNo}）` : '—'
    },
    { title: 'AppId', dataIndex: 'appId', width: 190, ellipsis: true },
    { title: '商户订单号', dataIndex: 'merchantOrderNo', width: 180, ellipsis: true },
    { title: '交易说明', dataIndex: 'subject', ellipsis: true },
    { title: '金额', dataIndex: 'amountCent', width: 110, render: (v: number) => money(v) },
    {
      title: '渠道',
      dataIndex: 'channel',
      width: 120,
      render: (v: string) => channelLabels[v] ?? v
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => {
        const s = statusLabels[v] ?? { label: v, color: 'default' };
        return <Tag color={s.color}>{s.label}</Tag>;
      }
    },
    { title: '付款方', dataIndex: 'payerMasked', width: 120, render: (v?: string) => v || '—' },
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
      render: (_: unknown, row: OpsPaymentOrder) => (
        <Button type="link" onClick={() => void loadDetail(row.paymentOrderNo)}>详情</Button>
      )
    }
  ];

  return (
    <AuthGate routeKey="payments">
      <Card
        className={styles.card}
        title={
          <div>
            <Typography.Title level={3}>支付订单</Typography.Title>
            <Typography.Text type="secondary">全平台支付订单查询（只读）</Typography.Text>
          </div>
        }
        extra={<Button onClick={() => void payments.refetch()}>刷新</Button>}
      >
        <div className={styles.filters}>
          <Input
            placeholder="商户号"
            value={merchantNo}
            onChange={(e) => setMerchantNo(e.target.value)}
            style={{ width: 160 }}
            allowClear
          />
          <Input
            placeholder="商户名称"
            value={name}
            onChange={(e) => setName(e.target.value)}
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
          <Button type="primary" onClick={() => void payments.refetch()}>查询</Button>
        </div>
        {payments.isError ? (
          <Alert
            showIcon
            type="error"
            title="支付订单加载失败"
            description="请稍后重试；若问题持续，请使用请求标识联系管理员。"
            action={<Button onClick={() => void payments.refetch()}>重试</Button>}
          />
        ) : (
          <Table<OpsPaymentOrder>
            rowKey="paymentOrderNo"
            columns={columns}
            dataSource={payments.data?.items ?? []}
            loading={payments.isPending}
            scroll={{ x: 1200 }}
            locale={{ emptyText: <Empty description="暂无支付订单" /> }}
            pagination={{
              current: page + 1,
              pageSize: size,
              total: payments.data?.total ?? 0,
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
      <Drawer width={560} title="支付订单详情" open={!!detail} onClose={() => setDetail(undefined)}>
        {detail && (
          <Descriptions
            column={1}
            bordered
            size="small"
            items={[
              { key: 'no', label: '支付订单号', children: detail.paymentOrderNo },
              {
                key: 'merchant',
                label: '商户',
                children: detail.merchantName
                  ? `${detail.merchantName}（${detail.merchantNo}）`
                  : '—'
              },
              { key: 'app', label: 'AppId', children: detail.appId },
              { key: 'merchantOrderNo', label: '商户订单号', children: detail.merchantOrderNo ?? '—' },
              { key: 'subject', label: '交易说明', children: detail.subject },
              { key: 'amount', label: '金额', children: money(detail.amountCent) },
              {
                key: 'channel',
                label: '渠道',
                children: channelLabels[detail.channel ?? ''] ?? detail.channel ?? '—'
              },
              {
                key: 'status',
                label: '状态',
                children: statusLabels[detail.status]?.label ?? detail.status
              },
              { key: 'payer', label: '付款方', children: detail.payerMasked ?? '—' },
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
