import { useNavigate } from '@umijs/max';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Input, Toast } from 'antd-mobile';
import { useEffect, useMemo, useState } from 'react';
import { AppShell } from '../components/AppShell';
import { AuthGate } from '../components/AuthGate';
import { InlineNotice } from '../components/InlineNotice';
import { ProblemNotice } from '../components/ProblemNotice';
import { ROUTES } from '../constants/routes';
import { queryKeys } from '../query/keys';
import { useSession } from '../hooks/useSession';
import { submitRealName } from '../services/account';
import styles from './feature.module.less';

function RealNameWorkspace(){
  const client=useQueryClient();const navigate=useNavigate();const {profile}=useSession();const [legalName,setLegalName]=useState('');const [idNumber,setIdNumber]=useState('');const [file,setFile]=useState<File|null>(null);
  const preview=useMemo(()=>file?URL.createObjectURL(file):null,[file]);
  useEffect(()=>()=>{if(preview)URL.revokeObjectURL(preview)},[preview]);
  const mutation=useMutation({mutationFn:()=>submitRealName({legalName:legalName.trim(),idNumber:idNumber.trim(),faceImage:file!}),onSuccess:async()=>{await Promise.all([client.refetchQueries({queryKey:queryKeys.session}),client.invalidateQueries({queryKey:queryKeys.capabilities}),client.invalidateQueries({queryKey:queryKeys.profile})]);Toast.show({icon:'success',content:'实名认证已完成'});navigate(ROUTES.me,{replace:true})}});
  const valid=legalName.trim().length>=2&&idNumber.trim().length>=6&&idNumber.trim().length<=32&&Boolean(file)&&Boolean(file&&file.size<=1_048_576&&file.type==='image/jpeg');
  if(profile?.realNameVerified){return <AppShell title="实名认证" backTo={ROUTES.me}><div className={styles.page}><section className={styles.intro}><h1>身份已认证</h1><p>你的沙箱身份已经核验，无需重复提交。资金功能将直接使用当前认证状态。</p></section><section className={styles.card}><div className={styles.success}>认证状态正常，已可使用转账、收付款与银行卡功能。</div><div className={styles.actions}><Button block color="primary" size="large" onClick={()=>navigate(ROUTES.me,{replace:true})}>返回我的</Button><Button block fill="outline" onClick={()=>navigate(ROUTES.home,{replace:true})}>回到首页</Button></div></section></div></AppShell>}
  return <AppShell title="实名认证" backTo={ROUTES.me}><div className={styles.page}>
    <section className={styles.intro}><h1>完成沙箱身份认证</h1><p>这里只验证演示流程：姓名至少 2 个字，测试证件号可填写任意 6–32 位内容，并上传 1 MB 以内 JPEG 测试图片。请勿提交真实证件。</p></section>
    {mutation.data?.status==='VERIFIED'?<div className={styles.success}>认证成功：{mutation.data.legalNameMasked||'身份已核验'}。现在可以继续设置支付密码并使用资金功能。</div>:null}
    <section className={styles.card}><Button fill="outline" size="small" onClick={()=>{setLegalName('测试用户');setIdNumber(`TEST${Date.now().toString().slice(-8)}`)}}>填入沙箱测试信息</Button><div className={styles.field}><label htmlFor="legal-name">测试姓名</label><Input id="legal-name" value={legalName} maxLength={64} placeholder="至少 2 个字" onChange={setLegalName}/></div><div className={styles.field}><label htmlFor="id-number">测试证件号</label><Input id="id-number" value={idNumber} maxLength={32} placeholder="任意 6–32 位测试内容" onChange={setIdNumber}/><p className={styles.hint}>沙箱不校验真实身份证格式与校验位。</p></div><div className={styles.field}><label className={styles.upload}>上传 JPEG 测试照片<input type="file" accept="image/jpeg" capture="user" onChange={(event)=>setFile(event.target.files?.[0]??null)}/></label>{file?<p className={styles.hint}>{file.name} · {(file.size/1024).toFixed(0)} KB{file.type!=='image/jpeg'?' · 请选择 JPEG 格式':''}</p>:null}{preview?<img className={styles.preview} src={preview} alt="待提交的人脸照片预览"/>:null}</div><InlineNotice>测试照片只随本次请求流式发送，不落盘；格式必须为 JPEG，大小不超过 1 MB。</InlineNotice>{mutation.isError?<ProblemNotice error={mutation.error}/>:null}<div className={styles.actions}><Button block color="primary" size="large" loading={mutation.isPending} disabled={!valid} onClick={()=>mutation.mutate()}>提交沙箱认证</Button>{!valid?<p className={styles.hint}>请完成测试姓名、6–32 位测试证件号，并选择合规 JPEG 图片。</p>:null}</div></section>
  </div></AppShell>
}
export default function RealNamePage(){return <AuthGate><RealNameWorkspace/></AuthGate>}
