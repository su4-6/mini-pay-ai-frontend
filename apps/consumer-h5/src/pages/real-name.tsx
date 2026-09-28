import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Input, Toast } from 'antd-mobile';
import { useEffect, useMemo, useState } from 'react';
import { AppShell } from '../components/AppShell';
import { AuthGate } from '../components/AuthGate';
import { InlineNotice } from '../components/InlineNotice';
import { ProblemNotice } from '../components/ProblemNotice';
import { ROUTES } from '../constants/routes';
import { queryKeys } from '../query/keys';
import { submitRealName } from '../services/account';
import styles from './feature.module.less';

function RealNameWorkspace(){
  const client=useQueryClient();const [legalName,setLegalName]=useState('');const [idNumber,setIdNumber]=useState('');const [file,setFile]=useState<File|null>(null);
  const preview=useMemo(()=>file?URL.createObjectURL(file):null,[file]);
  useEffect(()=>()=>{if(preview)URL.revokeObjectURL(preview)},[preview]);
  const mutation=useMutation({mutationFn:()=>submitRealName({legalName:legalName.trim(),idNumber:idNumber.trim(),faceImage:file!}),onSuccess:async()=>{await Promise.all([client.invalidateQueries({queryKey:queryKeys.session}),client.invalidateQueries({queryKey:queryKeys.capabilities}),client.invalidateQueries({queryKey:queryKeys.profile})]);Toast.show({icon:'success',content:'实名认证已提交'})}});
  const valid=legalName.trim().length>=2&&idNumber.trim().length>=6&&Boolean(file)&&Boolean(file&&file.size<=1_048_576);
  return <AppShell title="实名认证" backTo={ROUTES.me}><div className={styles.page}>
    <section className={styles.intro}><h1>完成实名认证</h1><p>用于转账、收付款和银行卡能力。当前线上为演示沙箱，请只使用测试信息，不要提交真实证件。</p></section>
    {mutation.data?.status==='VERIFIED'?<div className={styles.success}>认证成功：{mutation.data.legalNameMasked||'身份已核验'}。现在可以继续设置支付密码并使用资金功能。</div>:null}
    <section className={styles.card}><div className={styles.field}><label htmlFor="legal-name">姓名</label><Input id="legal-name" value={legalName} maxLength={64} placeholder="请输入测试姓名" onChange={setLegalName}/></div><div className={styles.field}><label htmlFor="id-number">证件号码</label><Input id="id-number" value={idNumber} maxLength={32} placeholder="沙箱允许 6–32 位测试号码" onChange={setIdNumber}/></div><div className={styles.field}><label className={styles.upload}>选择或拍摄正面照片<input type="file" accept="image/jpeg" capture="user" onChange={(event)=>setFile(event.target.files?.[0]??null)}/></label>{file?<p className={styles.hint}>{file.name} · {(file.size/1024).toFixed(0)} KB</p>:null}{preview?<img className={styles.preview} src={preview} alt="待提交的人脸照片预览"/>:null}</div><InlineNotice>照片仅随本次请求流式发送，不保存在 H5；限 JPEG、1 MB 以内。</InlineNotice>{mutation.isError?<ProblemNotice error={mutation.error}/>:null}<div className={styles.actions}><Button block color="primary" size="large" loading={mutation.isPending} disabled={!valid} onClick={()=>mutation.mutate()}>确认提交认证</Button></div></section>
  </div></AppShell>
}
export default function RealNamePage(){return <AuthGate><RealNameWorkspace/></AuthGate>}
