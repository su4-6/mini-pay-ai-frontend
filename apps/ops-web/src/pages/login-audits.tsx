import { useState } from 'react';
import { Alert, Button, Card, Empty, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import type { LoginAuditItem } from '@minipay/api-contracts';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { AuthGate } from '../components/AuthGate';
import { getLoginAudits } from '../services/auth';
import styles from './login-audits.module.less';

const resultLabels: Record<string, { label: string; color: string }> = {
  SUCCESS: { label: '成功', color: 'green' },
  REJECTED: { label: '密码或验证码错误', color: 'red' },
  LOCKED: { label: '账号已锁定', color: 'orange' },
  DISABLED: { label: '账号已禁用', color: 'default' },
  ROLE_DENIED: { label: '无该入口权限', color: 'volcano' },
  // 后端结果码 → 中文（变更 #45）：SMS_FAILED / CAPTCHA_FAILED 原来直接显示英文
  SMS_FAILED: { label: '短信验证码错误', color: 'red' },
  CAPTCHA_FAILED: { label: '图形验证码错误', color: 'red' },
  PASSWORD_FAILED: { label: '密码错误', color: 'red' },
  AUTHENTICATION_CHALLENGE_REJECTED: { label: '认证校验未通过', color: 'red' },
  IDENTITY_UNAVAILABLE: { label: '身份服务不可用', color: 'orange' },
  RATE_LIMITED: { label: '请求过于频繁', color: 'orange' }
};
// 未知结果码统一显示为中文，避免界面出现英文内部码
const resultOf = (value: string) =>
  resultLabels[value] ?? { label: value === 'SUCCESS' ? '成功' : '登录失败', color: 'red' };

const columns: TableColumnsType<LoginAuditItem> = [
  {
    title: '登录时间',
    dataIndex: 'occurredAt',
    width: 190,
    render: (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm:ss')
  },
  {
    title: '管理员',
    dataIndex: 'displayName',
    width: 160,
    render: (value?: string) => value || '未知账号'
  },
  {
    title: '登录方式',
    dataIndex: 'authenticationMethod',
    width: 130,
    render: (value: LoginAuditItem['authenticationMethod']) =>
      value === 'SMS' ? '手机验证码' : '手机号密码'
  },
  {
    title: '结果',
    dataIndex: 'result',
    width: 120,
    render: (value: string) => {
      const result = resultOf(value);
      return <Tag color={result.color}>{result.label}</Tag>;
    }
  },
  { title: '请求标识', dataIndex: 'requestId', ellipsis: true }
];

export default function LoginAuditsPage() {
  const [page, setPage] = useState(0);
  const size = 20;
  const audits = useQuery({
    queryKey: ['login-audits', page, size],
    queryFn: () => getLoginAudits(page, size)
  });

  return (
    <AuthGate routeKey="login-audits">
      <Card
        className={styles.card}
        title={
          <div>
            <Typography.Title level={4}>登录审计</Typography.Title>
            <Typography.Text type="secondary">查看运营管理员登录结果与安全事件</Typography.Text>
          </div>
        }
        extra={<Button onClick={() => void audits.refetch()}>刷新</Button>}
      >
        {audits.isError ? (
          <Alert
            showIcon
            type="error"
            title="审计记录加载失败"
            description="请稍后重试；若问题持续，请使用请求标识联系管理员。"
            action={<Button onClick={() => void audits.refetch()}>重试</Button>}
          />
        ) : (
          <Table<LoginAuditItem>
            rowKey="auditId"
            columns={columns}
            dataSource={audits.data?.items ?? []}
            loading={audits.isPending}
            scroll={{ x: 760 }}
            locale={{ emptyText: <Empty description="暂无登录审计记录" /> }}
            pagination={{
              current: page + 1,
              pageSize: size,
              total: audits.data?.total ?? 0,
              showSizeChanger: false,
              showTotal: (total) => `共 ${total} 条`,
              onChange: (nextPage) => setPage(nextPage - 1)
            }}
          />
        )}
      </Card>
    </AuthGate>
  );
}
