import { useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { api } from '@/lib/api/endpoints';
import { MarketCard } from '@/components/MarketCard';
import { Spinner } from '@/components/Spinner';
import { ErrorView } from '@/components/ErrorView';

const ORDERS: { value: string; label: string }[] = [
  { value: 'volume24hr', label: '24h volume' },
  { value: 'volume', label: 'All-time volume' },
  { value: 'liquidity', label: 'Liquidity' },
  { value: 'end_date', label: 'End date' },
];

export function MarketsPage() {
  const [order, setOrder] = useState('volume24hr');
  const [search, setSearch] = useState('');

  const q = useInfiniteQuery({
    queryKey: ['markets', order, search],
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      api.listMarkets({ page: pageParam as number, size: 20, order, search: search || undefined }),
    getNextPageParam: (last, all) => (last.hasMore ? all.length : undefined),
  });

  return (
    <div className="space-y-4">
      <header className="pt-2">
        <h1 className="text-2xl font-semibold">Markets</h1>
      </header>

      <div className="space-y-2">
        <input
          type="search"
          placeholder="Search markets"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full rounded-lg bg-tg-secondary px-3 py-2 text-sm outline-none placeholder:text-tg-hint focus:ring-2 focus:ring-tg-button/40"
        />
        <div className="flex gap-2 overflow-x-auto pb-1">
          {ORDERS.map((o) => (
            <button
              key={o.value}
              onClick={() => setOrder(o.value)}
              className={
                o.value === order
                  ? 'whitespace-nowrap rounded-full bg-tg-button px-3 py-1 text-xs font-medium text-tg-buttonText'
                  : 'whitespace-nowrap rounded-full bg-tg-secondary px-3 py-1 text-xs font-medium text-tg-text'
              }
            >
              {o.label}
            </button>
          ))}
        </div>
      </div>

      {q.isLoading && <Spinner label="Loading markets…" />}
      {q.error && <ErrorView message="Failed to load markets" onRetry={() => q.refetch()} />}

      <div className="grid gap-2">
        {q.data?.pages.flatMap((p) => p.items).map((m) => (
          <MarketCard key={m.conditionId} market={m} />
        ))}
      </div>

      {q.hasNextPage && (
        <button
          onClick={() => q.fetchNextPage()}
          disabled={q.isFetchingNextPage}
          className="w-full rounded-lg bg-tg-secondary py-2 text-sm font-medium text-tg-text disabled:opacity-50"
        >
          {q.isFetchingNextPage ? 'Loading…' : 'Load more'}
        </button>
      )}
    </div>
  );
}
