import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from '@umijs/max';
import { Button, Dialog, Input, Toast } from 'antd-mobile';
import { useState } from 'react';
import { AppShell } from '../components/AppShell';
import { AsyncState } from '../components/AsyncState';
import { AuthGate } from '../components/AuthGate';
import { InlineNotice } from '../components/InlineNotice';
import { PayPasswordField } from '../components/PayPasswordField';
import { ProblemNotice } from '../components/ProblemNotice';
import { ROUTES } from '../constants/routes';
import { queryKeys } from '../query/keys';
import { useSession } from '../hooks/useSession';
import { bindBankCard, queryBankBalance, unbindBankCard } from '../services/funding';
import { fetchBankCards } from '../services/wallet';
import { formatFenWithSymbol } from '../utils/money';
import styles from './feature.module.less';

function BankCardsWorkspace(){
  const navigate=useNavigate();
  const {profile}=useSession();const realNameVerified=profile?.realNameVerified??false;const payPasswordSet=profile?.payPasswordSet??false;
  const client=useQueryClient();const cards=useQuery({queryKey:queryKeys.bankCards,queryFn:fetchBankCards,enabled:realNameVerified});
  const [showBind,setShowBind]=useState(false);const [holderName,setHolderName]=useState('');const [cardNumber,setCardNumber]=useState('');const [code,setCode]=useState('');const [selected,setSelected]=useState<string|null>(null);const [password,setPassword]=useState('');
  const bind=useMutation({mutationFn:()=>bindBankCard({holderName:holderName.trim(),cardNumber:cardNumber.replace(/\s/g,''),verificationCode:code}),onSuccess:async()=>{await client.invalidateQueries({queryKey:queryKeys.bankCards});setShowBind(false);setCardNumber('');setCode('');Toast.show({icon:'success',content:'银行卡已绑定'})}});
  const balance=useMutation({mutationFn:()=>queryBankBalance(selected!,password),onSuccess:(value)=>{setPassword('');Dialog.alert({title:'银行卡余额',content:`可用余额 ${formatFenWithSymbol(value.availableFen)} ${value.currency}`})},onError:()=>setPassword('')});
  const remove=useMutation({mutationFn:unbindBankCard,onSuccess:async()=>{await client.invalidateQueries({queryKey:queryKeys.bankCards});Toast.show({content:'银行卡已解绑'})}});
  return <AppShell title="银行卡" backTo={ROUTES.home}><div className={styles.page}>
    <section className={styles.intro}><h1>银行卡管理</h1><p>演示环境使用沙箱银行卡。查询余额需要支付密码，密码仅发送给身份服务换取一次性授权。</p></section>
    {!realNameVerified?<InlineNotice tone="warning">请先完成沙箱实名认证，再绑定和使用银行卡。<Button size="mini" fill="none" color="primary" onClick={()=>navigate(ROUTES.realName)}>去实名认证</Button></InlineNotice>:!payPasswordSet?<InlineNotice tone="warning">银行卡余额查询与资金操作前，请先设置支付密码。<Button size="mini" fill="none" color="primary" onClick={()=>navigate(ROUTES.payPassword)}>去设置</Button></InlineNotice>:null}
    <AsyncState loading={cards.isLoading} error={cards.error} onRetry={()=>void cards.refetch()}>
      {(cards.data?.length??0)===0?<div className={styles.empty}>还没有银行卡，绑定后即可体验充值、提现和银行卡余额查询。</div>:cards.data?.map(card=><article className={styles.bankCard} key={card.cardId}><div className={styles.bankTop}><strong>{card.bankName}</strong><span>{card.cardType||'储蓄卡'} · {card.status}</span></div><p className={styles.cardNo}>{card.maskedCardNo}</p><div className={styles.bankActions}><Button size="small" fill="outline" style={{color:'#fff',borderColor:'rgba(255,255,255,.55)'}} disabled={!realNameVerified||!payPasswordSet} onClick={()=>{setSelected(card.cardId);setPassword('')}}>查询余额</Button><Button size="small" fill="none" style={{color:'#fff'}} loading={remove.isPending} onClick={()=>void Dialog.confirm({title:'解绑银行卡',content:`确认解绑 ${card.bankName} ${card.maskedCardNo}？`,confirmText:'确认解绑'}).then(ok=>{if(ok)remove.mutate(card.cardId)})}>解绑</Button></div></article>)}
    </AsyncState>
    {selected?<section className={styles.card}><h2>验证支付密码</h2><PayPasswordField value={password} onChange={setPassword} autoFocus disabled={balance.isPending}/>{balance.isError?<ProblemNotice error={balance.error}/>:null}<div className={styles.actions}><Button block color="primary" loading={balance.isPending} disabled={!payPasswordSet||!/^\d{6}$/.test(password)} onClick={()=>balance.mutate()}>安全查询余额</Button><Button block fill="none" onClick={()=>{setSelected(null);setPassword('')}}>取消</Button></div></section>:null}
    {showBind?<section className={styles.card}><h2>绑定沙箱银行卡</h2><div className={styles.field}><label>持卡人姓名</label><Input value={holderName} maxLength={64} onChange={setHolderName}/></div><div className={styles.field}><label>银行卡号</label><Input value={cardNumber} inputMode="numeric" maxLength={23} placeholder="16–23 位数字" onChange={setCardNumber}/></div><div className={styles.field}><label>银行验证码</label><Input value={code} inputMode="numeric" maxLength={6} placeholder="演示验证码 123456" onChange={setCode}/></div>{bind.isError?<ProblemNotice error={bind.error}/>:null}<div className={styles.actions}><Button block color="primary" loading={bind.isPending} disabled={!realNameVerified||holderName.trim().length<2||!/^[0-9 ]{16,23}$/.test(cardNumber)||!/^\d{6}$/.test(code)} onClick={()=>bind.mutate()}>确认绑定</Button><Button block fill="none" onClick={()=>setShowBind(false)}>取消</Button></div></section>:<Button block color="primary" size="large" disabled={!realNameVerified} onClick={()=>setShowBind(true)}>添加银行卡</Button>}
  </div></AppShell>
}
export default function BankCardsPage(){return <AuthGate><BankCardsWorkspace/></AuthGate>}
