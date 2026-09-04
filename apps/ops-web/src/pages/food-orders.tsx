import { useState } from 'react';
import { Alert, Button, Card, Descriptions, Drawer, Empty, Input, Select, Table, Tag, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import type { OpsFoodOrder } from '@minipay/api-contracts';
import { AuthGate } from '../components/AuthGate';
import { getOpsFoodOrder, getOpsFoodOrders } from '../services/ops';
import styles from './orders.module.less';

const money = (cent = 0) => `¥ ${(cent / 100).toFixed(2)}`;
const labels: Record<string, string> = {
  UNPAID: '待支付', PAID: '已支付', SUCCEEDED: '成功', FAILED: '失败',
  PENDING: '待处理', PREPARING: '备餐中', DELIVERING: '配送中', DELIVERED: '已送达',
  NONE: '无退款', PROCESSING: '处理中', REFUNDED: '已退款', CANCELLED: '已取消'
};
const zh = (value?: string | null) => value ? labels[value] ?? value : '—';

export default function FoodOrdersPage() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [orderNo, setOrderNo] = useState('');
  const [paymentStatus, setPaymentStatus] = useState<string>();
  const [detailId, setDetailId] = useState<string>();
  const orders = useQuery({
    queryKey: ['ops-food-orders', page, size, orderNo, paymentStatus],
    queryFn: () => getOpsFoodOrders({ page, size, orderNo: orderNo.trim() || undefined, paymentStatus })
  });
  const detail = useQuery({ queryKey: ['ops-food-order', detailId], queryFn: () => getOpsFoodOrder(detailId!), enabled: !!detailId });
  return <AuthGate routeKey="food-orders"><Card className={styles.card} title={<div><Typography.Title level={3}>外卖订单</Typography.Title><Typography.Text type="secondary">C 端外卖订单全链路只读查询</Typography.Text></div>} extra={<Button onClick={() => void orders.refetch()}>刷新</Button>}>
    <div className={styles.filters}><Input allowClear value={orderNo} onChange={e => setOrderNo(e.target.value)} placeholder="外卖订单号" style={{width:190}} /><Select allowClear value={paymentStatus} onChange={setPaymentStatus} placeholder="支付状态" options={['UNPAID','PAID','SUCCEEDED','FAILED'].map(value=>({value,label:zh(value)}))}/><Button type="primary" onClick={()=>{setPage(0);void orders.refetch();}}>查询</Button></div>
    {orders.isError?<Alert showIcon type="error" title="外卖订单加载失败" action={<Button onClick={()=>void orders.refetch()}>重试</Button>}/>:<Table<OpsFoodOrder> rowKey="orderRefId" loading={orders.isPending} dataSource={orders.data?.items??[]} locale={{emptyText:<Empty description="暂无外卖订单"/>}} scroll={{x:1050}} pagination={{current:page+1,pageSize:size,total:orders.data?.total??0,showSizeChanger:true,onChange:(next,nextSize)=>{setPage(next-1);setSize(nextSize);}}} columns={[
      {title:'外卖订单号',dataIndex:'externalOrderNo',width:190},{title:'金额',dataIndex:'amountCent',width:110,render:money},{title:'支付状态',dataIndex:'paymentStatus',render:(v)=><Tag>{zh(v)}</Tag>},{title:'履约状态',dataIndex:'fulfillmentStatus',render:zh},{title:'退款状态',dataIndex:'refundStatus',render:zh},{title:'创建时间',dataIndex:'createdAt',width:165,render:(v)=>dayjs(v).format('YYYY-MM-DD HH:mm')},{title:'操作',fixed:'right',render:(_,row)=><Button type="link" onClick={()=>setDetailId(row.orderRefId)}>详情</Button>}
    ]}/>} </Card><Drawer width={520} title="外卖订单详情" open={!!detailId} onClose={()=>setDetailId(undefined)} loading={detail.isLoading}>{detail.data&&<Descriptions bordered size="small" column={1} items={Object.entries(detail.data).map(([key,value])=>({key,label:key,children:key.endsWith('At')?dayjs(String(value)).format('YYYY-MM-DD HH:mm:ss'):key==='amountCent'?money(Number(value)):zh(String(value??''))}))}/>}</Drawer></AuthGate>;
}
