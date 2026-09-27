import { useNavigate } from '@umijs/max';
import { InfiniteScroll } from 'antd-mobile';
import { useInfiniteQuery } from '@tanstack/react-query';
import { AmountText } from '../components/AmountText';
import { AppShell } from '../components/AppShell';
import { AsyncState } from '../components/AsyncState';
import { AuthGate } from '../components/AuthGate';
import { Card } from '../components/Card';
import { StatusBadge } from '../components/StatusBadge';
import { ROUTES } from '../constants/routes';
import { queryKeys } from '../query/keys';
import { fetchTransferPage } from '../services/transfers';
import { formatDateTime } from '../utils/datetime';
import { transferStatusView } from '../utils/transfer-status';
import bills from './bills.module.less';

const PAGE_SIZE = 20;

function TransfersWorkspace() {
  const navigate = useNavigate();
  const query = useInfiniteQuery({
    queryKey: queryKeys.transferInfinite,
    queryFn: ({ pageParam }) => fetchTransferPage(pageParam, PAGE_SIZE),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined
  });

  const items = query.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <AppShell title="转账记录" subtitle="点击可查看详情并重新查询处理中的转账" backTo="/wallet">
      <Card tight>
        <AsyncState
          loading={query.isLoading}
          error={query.isError ? query.error : undefined}
          empty={items.length === 0}
          emptyText="暂无转账记录"
          onRetry={() => void query.refetch()}
        >
          <ul>
            {items.map((item) => {
              const view = transferStatusView(item.status);
              return (
                <li key={item.transferNo}>
                  <button
                    type="button"
                    className={bills.billButton}
                    onClick={() =>
                      navigate(`${ROUTES.transferDetail}?transferNo=${encodeURIComponent(item.transferNo)}`)
                    }
                  >
                    <span className={bills.billMain}>
                      <span className={bills.billTitle}>
                        {item.payeeMasked ? `转账给 ${item.payeeMasked}` : item.transferNo}
                      </span>
                      <span className={bills.billMeta}>
                        {formatDateTime(item.createdAt ?? item.updatedAt)} · {item.transferNo}
                      </span>
                    </span>
                    <span className={bills.billSide}>
                      <AmountText fen={item.amountFen} size="sm" />
                      <span className={bills.billBalance}>
                        <StatusBadge tone={view.tone} label={view.label} />
                      </span>
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        </AsyncState>
        <InfiniteScroll
          loadMore={async () => {
            await query.fetchNextPage();
          }}
          hasMore={Boolean(query.hasNextPage)}
        >
          <div className={bills.loadingRow}>
            {query.isFetchingNextPage ? '正在加载更多…' : query.hasNextPage ? '上拉加载更多' : '没有更多了'}
          </div>
        </InfiniteScroll>
      </Card>
    </AppShell>
  );
}

export default function TransfersPage() {
  return (
    <AuthGate>
      <TransfersWorkspace />
    </AuthGate>
  );
}
