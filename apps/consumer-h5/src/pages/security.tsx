import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Input, Toast } from 'antd-mobile';
import { useState } from 'react';
import { AppShell } from '../components/AppShell';
import { AsyncState } from '../components/AsyncState';
import { AuthGate } from '../components/AuthGate';
import { InlineNotice } from '../components/InlineNotice';
import { PayPasswordField } from '../components/PayPasswordField';
import { ProblemNotice } from '../components/ProblemNotice';
import { ROUTES } from '../constants/routes';
import { queryKeys } from '../query/keys';
import {
  changePaymentPassword,
  confirmPhoneChange,
  fetchAccountSecurity,
  requestPaymentPasswordChange,
  requestPhoneChange,
  verifyPaymentPasswordChange
} from '../services/account';
import styles from './feature.module.less';

function SecurityWorkspace(){
  const client=useQueryClient();const overview=useQuery({queryKey:queryKeys.accountSecurity,queryFn:fetchAccountSecurity});const [tab,setTab]=useState<'PHONE'|'PASSWORD'>('PHONE');const [mobile,setMobile]=useState('');const [code,setCode]=useState('');const [challengeId,setChallengeId]=useState('');const [verificationToken,setVerificationToken]=useState('');const [newPassword,setNewPassword]=useState('');
  const reset=()=>{setCode('');setChallengeId('');setVerificationToken('');setNewPassword('')};
  const phoneChallenge=useMutation({mutationFn:()=>requestPhoneChange(mobile),onSuccess:value=>{setChallengeId(value.challengeId);if(value.demoCode)setCode(value.demoCode);Toast.show({content:'验证码已发送'})}});
  const phoneConfirm=useMutation({mutationFn:()=>confirmPhoneChange(mobile,challengeId,code),onSuccess:async()=>{reset();setMobile('');await Promise.all([client.invalidateQueries({queryKey:queryKeys.session}),client.invalidateQueries({queryKey:queryKeys.accountSecurity})]);Toast.show({icon:'success',content:'手机号已更新'})}});
  const passwordChallenge=useMutation({mutationFn:()=>requestPaymentPasswordChange(mobile),onSuccess:value=>{setChallengeId(value.challengeId);if(value.demoCode)setCode(value.demoCode);Toast.show({content:'验证码已发送'})}});
  const passwordVerify=useMutation({mutationFn:()=>verifyPaymentPasswordChange(challengeId,code),onSuccess:token=>setVerificationToken(token)});
  const passwordChange=useMutation({mutationFn:()=>changePaymentPassword(verificationToken,newPassword),onSuccess:async()=>{reset();setMobile('');await Promise.all([client.invalidateQueries({queryKey:queryKeys.session}),client.invalidateQueries({queryKey:queryKeys.accountSecurity})]);Toast.show({icon:'success',content:'支付密码已更新'})},onError:()=>setNewPassword('')});
  const activeError=phoneChallenge.error||phoneConfirm.error||passwordChallenge.error||passwordVerify.error||passwordChange.error;
  return <AppShell title="账户安全" backTo={ROUTES.me}><AsyncState loading={overview.isLoading} error={overview.error} onRetry={()=>void overview.refetch()}><div className={styles.page}>
    <section className={styles.card}><div className={styles.statusRow}><span>登录手机号</span><strong>{overview.data?.maskedMobile||'—'}</strong></div><div className={styles.statusRow}><span>支付密码</span><strong>{overview.data?.paymentPasswordSet?'已设置':'未设置'}</strong></div></section>
    <div className={styles.tabs}><button className={`${styles.tab} ${tab==='PHONE'?styles.tabActive:''}`} onClick={()=>{setTab('PHONE');reset()}}>修改手机号</button><button className={`${styles.tab} ${tab==='PASSWORD'?styles.tabActive:''}`} onClick={()=>{setTab('PASSWORD');reset()}}>修改支付密码</button></div>
    <section className={styles.card}>{tab==='PHONE'?<><h2>修改登录手机号</h2><div className={styles.field}><label>新手机号</label><Input inputMode="numeric" maxLength={11} value={mobile} onChange={setMobile}/></div>{challengeId?<><div className={styles.field}><label>短信验证码</label><Input inputMode="numeric" maxLength={6} value={code} onChange={setCode}/></div><Button block color="primary" loading={phoneConfirm.isPending} disabled={!/^\d{6}$/.test(code)} onClick={()=>phoneConfirm.mutate()}>确认修改</Button></>:<Button block color="primary" loading={phoneChallenge.isPending} disabled={!/^1[3-9]\d{9}$/.test(mobile)} onClick={()=>phoneChallenge.mutate()}>获取验证码</Button>}</>:<><h2>修改支付密码</h2><InlineNotice>为确认是本人操作，请重新输入当前完整手机号并通过短信验证。</InlineNotice><div className={styles.field} style={{marginTop:14}}><label>当前手机号</label><Input inputMode="numeric" maxLength={11} value={mobile} onChange={setMobile}/></div>{!challengeId?<Button block color="primary" loading={passwordChallenge.isPending} disabled={!/^1[3-9]\d{9}$/.test(mobile)} onClick={()=>passwordChallenge.mutate()}>获取验证码</Button>:!verificationToken?<><div className={styles.field}><label>短信验证码</label><Input inputMode="numeric" maxLength={6} value={code} onChange={setCode}/></div><Button block color="primary" loading={passwordVerify.isPending} disabled={!/^\d{6}$/.test(code)} onClick={()=>passwordVerify.mutate()}>验证身份</Button></>:<><div className={styles.field}><label>新支付密码</label><PayPasswordField value={newPassword} onChange={setNewPassword} disabled={passwordChange.isPending}/></div><Button block color="primary" loading={passwordChange.isPending} disabled={!/^\d{6}$/.test(newPassword)} onClick={()=>passwordChange.mutate()}>确认修改支付密码</Button></>}</>}{activeError?<ProblemNotice error={activeError}/>:null}</section>
  </div></AsyncState></AppShell>
}
export default function SecurityPage(){return <AuthGate><SecurityWorkspace/></AuthGate>}
