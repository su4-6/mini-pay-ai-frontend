import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Col, Empty, Radio, Row, Skeleton } from 'antd';
import type { OpsDashboardRange } from '@minipay/api-contracts';
import { lazy, Suspense, useState, type ReactNode } from 'react';
import dayjs from 'dayjs';
import { AuthGate } from '../components/AuthGate';
import { getOpsDashboard } from '../services/ops';
import styles from './index.module.less';

const TrendLineChart = lazy(() => import('../components/TrendLineChart'));

export function formatCent(amountCent: number): string {
  return (amountCent / 100).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
}

function Metric({ label, value, meta }: { label: string; value: ReactNode; meta?: ReactNode }) {
  return (
    <Card className={styles.metricCard}>
      <span className={styles.metricLabel}>{label}</span>
      <div className={styles.metricValue}>{value}</div>
      {meta !== undefined && <div className={styles.metricMeta}>{meta}</div>}
    </Card>
  );
}

function DashboardContent() {
  const [range, setRange] = useState<OpsDashboardRange>('7d');
  const dashboard = useQuery({
    queryKey: ['ops-dashboard', range],
    queryFn: () => getOpsDashboard(range)
  });

  if (dashboard.isPending) {
    return <Card><Skeleton active paragraph={{ rows: 8 }} /></Card>;
  }

  if (dashboard.isError || !dashboard.data) {
    return (
      <Alert
        showIcon
        type="error"
        title="运营数据加载失败"
        description="请稍后重试；若问题持续，请使用请求标识联系管理员。"
        action={<Button onClick={() => void dashboard.refetch()}>重试</Button>}
      />
    );
  }

  const data = dashboard.data;
  const trendData = data.trend.flatMap((item) => [
    { date: item.date, category: '支付金额', amount: item.paymentAmountCent / 100 },
    { date: item.date, category: '退款金额', amount: item.refundAmountCent / 100 }
  ]);
  const pendingItems = [
    ['异常支付', data.pending.abnormalPaymentCount],
    ['异常退款', data.pending.abnormalRefundCount],
    ['异常转账', data.pending.abnormalTransferCount],
    ['通知失败', data.pending.failedNotificationCount]
  ] as const;

  return (
    <>
      <div className={styles.pageHeading}>
        <div>
          <h1>运营平台总览</h1>
          <p>全平台交易、退款与活跃商户指标，不包含商户租户主页数据。</p>
          <p style={{ color: '#98a2b3', fontSize: 13 }}>
            统计范围：{data.from} 至 {data.to}（{data.timezone}） · 数据截至{' '}
            {dayjs(data.dataAsOf).format('YYYY-MM-DD HH:mm:ss')}
          </p>
        </div>
        <div className={styles.headerActions}>
          <Radio.Group
            value={range}
            optionType="button"
            buttonStyle="solid"
            options={[{ label: '近 7 天', value: '7d' }, { label: '近 30 天', value: '30d' }]}
            onChange={(event) => setRange(event.target.value as OpsDashboardRange)}
          />
          <Button loading={dashboard.isFetching} onClick={() => void dashboard.refetch()}>刷新</Button>
        </div>
      </div>

      <div className={styles.metricGrid}>
        <Metric label="平台交易金额" value={`¥ ${formatCent(data.summary.paymentAmountCent)}`} />
        <Metric label="成功交易笔数" value={data.summary.paymentCount} meta="笔" />
        <Metric label="支付成功率" value={`${(data.summary.successRateBasisPoints / 100).toFixed(2)}%`} />
        <Metric label="退款金额" value={`¥ ${formatCent(data.summary.refundAmountCent)}`} />
        <Metric label="活跃商户" value={data.summary.activeMerchantCount} meta="家" />
      </div>

      <Row gutter={[16, 16]} className={styles.dashboardBody}>
        <Col xs={24} xl={16}>
          <Card title="交易趋势" className={styles.panel}>
            {trendData.every((item) => item.amount === 0) ? (
              <Empty description="当前统计周期暂无交易数据" />
            ) : (
              <Suspense fallback={<Skeleton active paragraph={{ rows: 6 }} />}><TrendLineChart
                height={300}
                data={trendData}
                xField="date"
                yField="amount"
                colorField="category"
                axis={{ y: { labelFormatter: (value: number) => `¥${value}` } }}
                tooltip={{ title: 'date' }}
              /></Suspense>
            )}
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card title="待处理事项" className={styles.panel}>
            <div className={styles.pendingGrid}>
              {pendingItems.map(([label, value]) => (
                <div className={styles.pendingItem} key={label}>
                  <span>{label}</span>
                  <strong>{value}</strong>
                </div>
              ))}
            </div>
            {pendingItems.every(([, value]) => value === 0) ? (
              <Alert className={styles.pendingOk} type="success" showIcon title="暂无待处理异常" />
            ) : (
              <p className={styles.pendingHint}>待处理事项仅统计超时未终态订单和投递失败的商户通知。</p>
            )}
          </Card>
        </Col>
      </Row>
    </>
  );
}

export default function OperationsHomePage() {
  return (
    <AuthGate routeKey="dashboard">
      <DashboardContent />
    </AuthGate>
  );
}
