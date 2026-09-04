import { useState } from 'react';
import { Alert, Button, Card, Descriptions, Drawer, Empty, Input, Select, Statistic, Table, Tag, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import type { OpsCollectionRecord } from '@minipay/api-contracts';
import { AuthGate } from '../components/AuthGate';
import { getOpsCollectionRecord, getOpsCollectionRecords } from '../services/ops';
import styles from './orders.module.less';

const money = (cent = 0) => `¥ ${(cent / 100).toFixed(2)}`;
const labels: Record<string,string> = { PERSONAL_COLLECTION_CODE:'个人收款码',MERCHANT_PAYMENT:'商户收款',MERCHANT_REFUND:'商户退款',SUCCEEDED:'成功',FAILED:'失败',PROCESSING:'处理中',INCOME:'收入',EXPENSE:'支出' };
const zh = (value?:string|null) => value ? labels[value]??value : '—';

export default function CollectionRecordsPage(){
  const [page,setPage]=useState(0);const [size,setSize]=useState(20);const [businessNo,setBusinessNo]=useState('');const [type,setType]=useState<string>();const [billId,setBillId]=useState<string>();
  const records=useQuery({queryKey:['ops-collections',page,size,businessNo,type],queryFn:()=>getOpsCollectionRecords({page,size,businessNo:businessNo.trim()||undefined,type})});
  const detail=useQuery({queryKey:['ops-collection',billId],queryFn:()=>getOpsCollectionRecord(billId!),enabled:!!billId});const summary=records.data?.summary;
  return <AuthGate routeKey="collection-records"><div className={styles.summaryGrid}>{[['收款金额',summary?.collectionAmountCent],['退款金额',summary?.refundAmountCent],['净收款',summary?.netAmountCent]].map(([title,value])=><Card key={String(title)}><Statistic title={title} value={money(Number(value??0))}/></Card>)}</div><Card className={styles.card} title={<div><Typography.Title level={3}>收款记录</Typography.Title><Typography.Text type="secondary">个人与商户收款、退款记录（只读）</Typography.Text></div>} extra={<Button onClick={()=>void records.refetch()}>刷新</Button>}><div className={styles.filters}><Input allowClear value={businessNo} onChange={e=>setBusinessNo(e.target.value)} placeholder="业务单号" style={{width:190}}/><Select allowClear value={type} onChange={setType} placeholder="收款类型" options={[{value:'PERSONAL',label:'个人收款'},{value:'MERCHANT',label:'商户收款'}]}/><Button type="primary" onClick={()=>{setPage(0);void records.refetch();}}>查询</Button></div>{records.isError?<Alert showIcon type="error" title="收款记录加载失败"/>:<Table<OpsCollectionRecord> rowKey="billId" loading={records.isPending} dataSource={records.data?.items??[]} locale={{emptyText:<Empty description="暂无收款记录"/>}} pagination={{current:page+1,pageSize:size,total:records.data?.total??0,showSizeChanger:true,onChange:(next,nextSize)=>{setPage(next-1);setSize(nextSize);}}} columns={[{title:'业务单号',dataIndex:'businessNo'},{title:'类型',dataIndex:'source',render:zh},{title:'方向',dataIndex:'direction',render:zh},{title:'金额',dataIndex:'amountCent',render:money},{title:'状态',dataIndex:'status',render:(v)=><Tag>{zh(v)}</Tag>},{title:'发生时间',dataIndex:'occurredAt',render:(v)=>dayjs(v).format('YYYY-MM-DD HH:mm')},{title:'操作',render:(_,row)=><Button type="link" onClick={()=>setBillId(row.billId)}>详情</Button>}]}/>}</Card><Drawer width={520} title="收款记录详情" open={!!billId} onClose={()=>setBillId(undefined)} loading={detail.isLoading}>{detail.data&&<Descriptions bordered size="small" column={1} items={Object.entries(detail.data).map(([key,value])=>({key,label:key,children:key.endsWith('At')?dayjs(String(value)).format('YYYY-MM-DD HH:mm:ss'):key==='amountCent'||key==='balanceAfterCent'?money(Number(value)):zh(String(value??''))}))}/>}</Drawer></AuthGate>;
}
