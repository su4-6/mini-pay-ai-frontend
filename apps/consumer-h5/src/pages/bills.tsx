import { useInfiniteQuery } from '@tanstack/react-query';
import { InfiniteScroll } from 'antd-mobile';
import { useMemo, useState } from 'react';
import { AmountText } from '../components/AmountText';
import { AppShell } from '../components/AppShell';
import { AsyncState } from '../components/AsyncState';
import { AuthGate } from '../components/AuthGate';
import { Card } from '../components/Card';
import { queryKeys } from '../query/keys';
import { fetchBillPage } from '../services/wallet';
import { describeFailureCode } from '../services/problem';
import { formatDateTime } from '../utils/datetime';
import { formatFenWithSymbol } from '../utils/money';
import { billDirectionLabel } from '../utils/transfer-status';
import styles from './bills.module.less';

const PAGE_SIZE = 20;

type DirectionFilter = 'ALL' | 'DEBIT' | 'CREDIT';

const FILTERS: Array<{ key: DirectionFilter; label: string }> = [
  { key: 'ALL', label: '全部' },
  { key: 'DEBIT', label: '支出' },
  { key: 'CREDIT', label: '收入' }
];

function BillsWorkspace() {
  const [filter, setFilter] = useState<DirectionFilter>('ALL');

  const query = useInfiniteQuery({
    queryKey: queryKeys.billInfinite,
    queryFn: ({ pageParam }) => fetchBillPage(pageParam, PAGE_SIZE),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined
  });

  const bills = useMemo(() => query.data?.pages.flatMap((page) => page.items) ?? [], [query.data]);
  const filtered = useMemo(
    () =>
      filter === 'ALL'
        ? bills
        : bills.filter((bill) => {
            const income = ['CREDIT', 'IN', 'INCOME'].includes(bill.direction.toUpperCase());
            return filter === 'CREDIT' ? income : !income;
          }),
    [bills, filter]
  );

  const first = bills[0];

  return (
    <AppShell title="账单" subtitle="只读分页，按服务端游标加载" backTo="/wallet">
      {first ? (
        <div className={styles.summaryCard}>
          <div>
            <div className={styles.summaryLabel}>最新一笔</div>
            <div className={styles.summaryValue}>{first.counterpartyDisplay ?? first.businessType}</div>
          </div>
          <AmountText fen={first.amountFen} direction={first.direction} size="md" />
        </div>
      ) : null}

      <div className={styles.filterRow} role="tablist" aria-label="账单方向筛选">
        {FILTERS.map(({ key, label }) => (
          <button
            key={key}
            type="button"
            role="tab"
            aria-selected={filter === key}
            className={
              filter === key ? `${styles.filterChip} ${styles.filterChipActive}` : styles.filterChip
            }
            onClick={() => setFilter(key)}
          >
            {label}
          </button>
        ))}
      </div>

      <Card tight>
        <AsyncState
          loading={query.isLoading}
          error={query.isError ? query.error : undefined}
          empty={filtered.length === 0}
          emptyText={bills.length === 0 ? '暂无账单记录' : '当前筛选下没有记录'}
          onRetry={() => void query.refetch()}
        >
          <ul>
            {filtered.map((bill) => (
              <li key={bill.billId || bill.businessNo}>
                <div className={styles.billItem}>
                  <div className={styles.billMain}>
                    <div className={styles.billTitle}>{bill.counterpartyDisplay ?? bill.businessType}</div>
                    <div className={styles.billMeta}>
                      {billDirectionLabel(bill.direction)} · {formatDateTime(bill.occurredAt)}
                      {bill.remark ? ` · ${bill.remark}` : ''}
                    </div>
                    {bill.failureCode ? (
                      <div className={styles.billMeta}>失败原因：{describeFailureCode(bill.failureCode)}</div>
                    ) : null}
                  </div>
                  <div className={styles.billSide}>
                    <AmountText fen={bill.amountFen} direction={bill.direction} size="sm" />
                    {bill.balanceAfterFen !== undefined ? (
                      <div className={styles.billBalance}>
                        余额 {formatFenWithSymbol(bill.balanceAfterFen)}
                      </div>
                    ) : null}
                  </div>
                </div>
              </li>
            ))}
          </ul>
        </AsyncState>

        <InfiniteScroll
          loadMore={async () => {
            await query.fetchNextPage();
          }}
          hasMore={Boolean(query.hasNextPage) && filter === 'ALL'}
        >
          <div className={styles.loadingRow}>
            {query.isFetchingNextPage
              ? '正在加载更多…'
              : query.hasNextPage
                ? filter === 'ALL'
                  ? '上拉加载更多'
                  : '筛选模式下仅显示已加载的数据'
                : '没有更多了'}
          </div>
        </InfiniteScroll>
      </Card>
    </AppShell>
  );
}

export default function BillsPage() {
  return (
    <AuthGate>
      <BillsWorkspace />
    </AuthGate>
  );
}
