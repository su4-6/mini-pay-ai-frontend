import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Input, Toast } from 'antd-mobile';
import { useEffect, useState } from 'react';
import { AppShell } from '../components/AppShell';
import { AsyncState } from '../components/AsyncState';
import { AuthGate } from '../components/AuthGate';
import { ProblemNotice } from '../components/ProblemNotice';
import { ROUTES } from '../constants/routes';
import { queryKeys } from '../query/keys';
import { fetchProfile, updateProfile } from '../services/account';
import styles from './feature.module.less';

function ProfileWorkspace(){
  const client=useQueryClient();
  const query=useQuery({queryKey:queryKeys.profile,queryFn:fetchProfile});
  const [nickname,setNickname]=useState('');
  useEffect(()=>{if(query.data)setNickname(query.data.nickname)},[query.data]);
  const mutation=useMutation({mutationFn:()=>updateProfile(nickname.trim(),query.data?.version??0),onSuccess:async(value)=>{client.setQueryData(queryKeys.profile,value);await client.invalidateQueries({queryKey:queryKeys.session});Toast.show({icon:'success',content:'资料已更新'})}});
  return <AppShell title="个人资料" backTo={ROUTES.me}>
    <AsyncState loading={query.isLoading} error={query.error} onRetry={()=>void query.refetch()}>
      <div className={styles.page}>
        <section className={styles.card}><div className={styles.profileHead}><div className={styles.avatar}>{(query.data?.nickname||'M').slice(0,1)}</div><div><h2>{query.data?.nickname}</h2><p>MiniPay 号 {query.data?.miniPayNo||'—'}</p></div></div></section>
        <section className={styles.card}><h2>基础资料</h2><div className={styles.field}><label htmlFor="profile-nickname">昵称</label><Input id="profile-nickname" value={nickname} maxLength={20} onChange={setNickname}/><p className={styles.hint}>头像存储未启用，本期使用昵称首字母作为安全头像。</p></div>{mutation.isError?<ProblemNotice error={mutation.error}/>:null}<Button block color="primary" loading={mutation.isPending} disabled={!/^[\p{L}\p{N}_]{2,20}$/u.test(nickname.trim())||nickname===query.data?.nickname} onClick={()=>mutation.mutate()}>保存修改</Button></section>
        <section className={styles.card}><div className={styles.statusRow}><span>实名认证</span><strong>{query.data?.legalNameMasked||'未认证'}</strong></div><div className={styles.statusRow}><span>资料版本</span><strong>{query.data?.version??0}</strong></div></section>
      </div>
    </AsyncState>
  </AppShell>
}
export default function ProfilePage(){return <AuthGate><ProfileWorkspace/></AuthGate>}
