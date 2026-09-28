import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocation, useNavigate } from '@umijs/max';
import { Button, Input, Toast } from 'antd-mobile';
import { useState } from 'react';
import { AppShell } from '../components/AppShell';
import { AuthGate } from '../components/AuthGate';
import { InlineNotice } from '../components/InlineNotice';
import { PayPasswordField } from '../components/PayPasswordField';
import { ProblemNotice } from '../components/ProblemNotice';
import { ROUTES } from '../constants/routes';
import { queryKeys } from '../query/keys';
import { useSession } from '../hooks/useSession';
import { fetchFundingOrders, submitFunding } from '../services/funding';
import { fetchBankCards } from '../services/wallet';
import { sanitizeAmountInput, validateAmountInput, formatFenWithSymbol } from '../utils/money';
import styles from './feature.module.less';

function FundingWorkspace(){
  const navigate=useNavigate();
  const location=useLocation();
  const {profile}=useSession();const realNameVerified=profile?.realNameVerified??false;const payPasswordSet=profile?.payPasswordSet??false;const fundsReady=realNameVerified&&payPasswordSet;
  const client=useQueryClient();const [type,setType]=useState<'RECHARGE'|'WITHDRAWAL'>(()=>new URLSearchParams(location.search).get('type')==='WITHDRAWAL'?'WITHDRAWAL':'RECHARGE');const [cardId,setCardId]=useState('');const [amount,setAmount]=useState('');const [password,setPassword]=useState('');
  const cards=useQuery({queryKey:queryKeys.bankCards,queryFn:fetchBankCards,enabled:realNameVerified});const history=useQuery({queryKey:queryKeys.fundingOrders(type),queryFn:()=>fetchFundingOrders(type),enabled:realNameVerified});
  const parsed=validateAmountInput(amount);
  const mutation=useMutation({mutationFn:()=>submitFunding({type,bankCardId:cardId,amountFen:parsed.ok?parsed.fen:0,paymentPassword:password}),onSuccess:async(order)=>{setPassword('');await Promise.all([client.invalidateQueries({queryKey:queryKeys.walletRoot}),client.invalidateQueries({queryKey:queryKeys.fundingRoot})]);Toast.show({icon:'success',content:`${type==='RECHARGE'?'充值':'提现'}${order.status==='SUCCEEDED'?'成功':'已受理'}`})},onError:()=>setPassword('')});
  const canSubmit=cardId&&parsed.ok&&/^\d{6}$/.test(password);
  return <AppShell title="充值提现" backTo={ROUTES.home}><div className={styles.page}>
    <section className={styles.intro}><h1>沙箱资金操作</h1><p>每笔操作都使用支付密码换取与订单、金额绑定的一次性授权，不会直接修改余额。</p></section>
    {!realNameVerified?<InlineNotice tone="warning">请先完成沙箱实名认证，再设置支付密码后使用充值提现。<Button size="mini" fill="none" color="primary" onClick={()=>navigate(ROUTES.realName)}>去实名认证</Button></InlineNotice>:!payPasswordSet?<InlineNotice tone="warning">实名认证已完成，请先设置支付密码后继续。<Button size="mini" fill="none" color="primary" onClick={()=>navigate(ROUTES.payPassword)}>去设置</Button></InlineNotice>:null}
    <div className={styles.tabs}><button className={`${styles.tab} ${type==='RECHARGE'?styles.tabActive:''}`} onClick={()=>setType('RECHARGE')}>充值</button><button className={`${styles.tab} ${type==='WITHDRAWAL'?styles.tabActive:''}`} onClick={()=>setType('WITHDRAWAL')}>提现</button></div>
    <section className={styles.card}><h2>{type==='RECHARGE'?'充值到钱包':'提现到银行卡'}</h2>{(cards.data?.length??0)===0?<InlineNotice tone="warning">请先绑定一张沙箱银行卡。</InlineNotice>:<><div className={styles.field}><label htmlFor="fund-card">选择银行卡</label><select id="fund-card" value={cardId} onChange={e=>setCardId(e.target.value)} style={{height:44,border:'1px solid #d7e0ec',borderRadius:12,padding:'0 10px',background:'#fff'}}><option value="">请选择</option>{cards.data?.map(card=><option key={card.cardId} value={card.cardId}>{card.bankName} {card.maskedCardNo}</option>)}</select></div><div className={styles.field}><label htmlFor="fund-amount">金额（元）</label><Input id="fund-amount" inputMode="decimal" value={amount} placeholder="0.00" onChange={v=>setAmount(sanitizeAmountInput(v))}/></div><div className={styles.field}><label>支付密码</label><PayPasswordField value={password} onChange={setPassword} disabled={mutation.isPending}/></div>{mutation.isError?<ProblemNotice error={mutation.error}/>:null}<Button block color="primary" size="large" loading={mutation.isPending} disabled={!fundsReady||!canSubmit} onClick={()=>mutation.mutate()}>确认{type==='RECHARGE'?'充值':'提现'}{parsed.ok?` ${formatFenWithSymbol(parsed.fen)}`:''}</Button></>}</section>
    <section className={styles.card}><h2>最近{type==='RECHARGE'?'充值':'提现'}记录</h2>{history.isLoading?<p className={styles.hint}>加载中…</p>:history.isError?<ProblemNotice error={history.error}/>:history.data?.items.length?history.data.items.map(order=><div className={styles.statusRow} key={order.orderId}><span>{order.orderNo||order.orderId.slice(0,8)}</span><strong>{formatFenWithSymbol(order.amountFen)} · {order.status}</strong></div>):<div className={styles.empty}>暂无记录</div>}</section>
  </div></AppShell>
}
export default function FundingPage(){return <AuthGate><FundingWorkspace/></AuthGate>}
