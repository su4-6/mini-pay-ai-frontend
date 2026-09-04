import { useState } from 'react';
import { Alert, Button, Card, Descriptions, Drawer, Empty, Input, Select, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import type {
  OpsRecharge, OpsRechargeDetail, OpsRechargePage,
  OpsWithdrawal, OpsWithdrawalDetail, OpsWithdrawalPage
} from '@minipay/api-contracts';
import { AuthGate } from './AuthGate';
import type { OpsRouteKey } from '../config/navigation';
import styles from '../pages/orders.module.less';

type Kind = 'recharge' | 'withdrawal';
type Row = OpsRecharge | OpsWithdrawal;
type Detail = OpsRechargeDetail | OpsWithdrawalDetail;
type Page = OpsRechargePage | OpsWithdrawalPage;

interface Props {
  kind: Kind;
  routeKey: OpsRouteKey;
  list: (filters: { page: number; size: number; orderNo?: string; status?: string }) => Promise<Page>;
  get: (orderNo: string) => Promise<Detail>;
}

const statusLabels: Record<string, { label: string; color: string }> = {
  CREATED: { label: '待处理', color: 'default' },
  PENDING_CONFIRMATION: { label: '待确认', color: 'warning' },
  PROCESSING: { label: '处理中', color: 'processing' },
  SUCCEEDED: { label: '成功', color: 'success' },
  FAILED: { label: '失败', color: 'error' },
  CANCELLED: { label: '已取消', color: 'default' }
};
const money = (cent = 0) => `¥ ${(cent / 100).toFixed(2)}`;
const orderNo = (row: Row) => 'rechargeNo' in row ? row.rechargeNo : row.withdrawalNo;

export function ConsumerFundsOrders({ kind, routeKey, list, get }: Props) {
  const recharge = kind === 'recharge';
  const title = recharge ? '充值订单' : '提现订单';
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [status, setStatus] = useState<string>();
  const [keyword, setKeyword] = useState('');
  const [detail, setDetail] = useState<Detail>();
  const query = useQuery({
    queryKey: [`ops-${kind}`, page, size, status, keyword],
    queryFn: () => list({ page, size, status, orderNo: keyword.trim() || undefined })
  });
  const columns: TableColumnsType<Row> = [
    { title: `${recharge ? '充值' : '提现'}单号`, width: 205, ellipsis: true, render: (_, row) => orderNo(row) },
    { title: '用户', dataIndex: 'userMasked', width: 140, render: (v?: string) => v || '—' },
    { title: '金额', dataIndex: 'amountCent', width: 120, render: money },
    { title: '银行', dataIndex: 'bankName', width: 140, render: (v?: string) => v || '—' },
    { title: '银行卡', dataIndex: 'bankCardMasked', width: 170, render: (v?: string) => v || '—' },
    ...(recharge ? [{ title: '渠道', dataIndex: 'channel', width: 120 }] : []),
    { title: '状态', dataIndex: 'status', width: 105, render: (v: string) => { const item = statusLabels[v] ?? { label: v, color: 'default' }; return <Tag color={item.color}>{item.label}</Tag>; } },
    { title: '创建时间', dataIndex: 'createdAt', width: 170, render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm') },
    { title: '操作', fixed: 'right', width: 80, render: (_, row) => <Button type="link" onClick={async () => setDetail(await get(orderNo(row)))}>详情</Button> }
  ];
  return <AuthGate routeKey={routeKey}>
    <Card className={styles.card} title={<div><Typography.Title level={3}>{title}</Typography.Title><Typography.Text type="secondary">平台用户{recharge ? '充值入账' : '提现出账'}记录（只读）</Typography.Text></div>} extra={<Button onClick={() => void query.refetch()}>刷新</Button>}>
      <div className={styles.filters}>
        <Input allowClear value={keyword} onChange={e => setKeyword(e.target.value)} placeholder={`${recharge ? '充值' : '提现'}单号`} style={{ width: 210 }} />
        <Select allowClear value={status} onChange={setStatus} placeholder="状态" style={{ width: 140 }} options={Object.entries(statusLabels).map(([value, item]) => ({ value, label: item.label }))} />
        <Button type="primary" onClick={() => { setPage(0); void query.refetch(); }}>查询</Button>
      </div>
      {query.isError ? <Alert showIcon type="error" title={`${title}加载失败`} action={<Button onClick={() => void query.refetch()}>重试</Button>} /> :
        <Table<Row> rowKey={orderNo} columns={columns} dataSource={query.data?.items ?? []} loading={query.isPending} scroll={{ x: 1100 }} locale={{ emptyText: <Empty description={`暂无${title}`} /> }} pagination={{ current: page + 1, pageSize: size, total: query.data?.total ?? 0, showSizeChanger: true, showTotal: total => `共 ${total} 笔`, onChange: (next, nextSize) => { setPage(next - 1); setSize(nextSize); } }} />}
    </Card>
    <Drawer width={560} title={`${title}详情`} open={!!detail} onClose={() => setDetail(undefined)}>
      {detail && <Descriptions bordered size="small" column={1} items={[
        { key: 'no', label: `${recharge ? '充值' : '提现'}单号`, children: orderNo(detail) },
        { key: 'user', label: '用户', children: detail.userMasked || '—' },
        { key: 'amount', label: '金额', children: money(detail.amountCent) },
        { key: 'bank', label: '银行', children: detail.bankName || '—' },
        { key: 'card', label: '银行卡', children: detail.bankCardMasked || '—' },
        ...('channel' in detail ? [{ key: 'channel', label: '渠道', children: detail.channel }] : []),
        ...('bankRequestNo' in detail ? [{ key: 'bankRequestNo', label: '银行请求号', children: detail.bankRequestNo || '—' }] : []),
        { key: 'status', label: '状态', children: statusLabels[detail.status]?.label ?? detail.status },
        { key: 'failure', label: '失败原因', children: detail.failureCode || '—' },
        { key: 'created', label: '创建时间', children: dayjs(detail.createdAt).format('YYYY-MM-DD HH:mm:ss') },
        { key: 'updated', label: '更新时间', children: dayjs(detail.updatedAt).format('YYYY-MM-DD HH:mm:ss') }
      ]} />}
    </Drawer>
  </AuthGate>;
}
