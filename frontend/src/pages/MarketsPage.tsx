import { useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { api } from '@/lib/api/endpoints';
import { MarketCard } from '@/components/MarketCard';
import { Spinner } from '@/components/Spinner';
import { ErrorView } from '@/components/ErrorView';
import {
  MARKET_GROUPS,
  type MarketGroupId,
  marketGroupById,
} from '@/features/markets/marketGroups';
import { useT, type MessageKey } from '@/lib/i18n';

const ORDERS: { value: string; label: MessageKey }[] = [
  { value: 'volume24hr', label: 'markets.sort.volume24hr' },
  { value: 'volume', label: 'markets.sort.volume' },
  { value: 'liquidity', label: 'markets.sort.liquidity' },
  { value: 'end_date', label: 'markets.sort.endDate' },
  { value: 'startDate', label: 'markets.sort.newest' },
];

export function MarketsPage() {
  const t = useT();
  const [groupId, setGroupId] = useState<MarketGroupId>('all');
  const group = marketGroupById(groupId);

  const [order, setOrder] = useState(group.defaultOrder);
  const [ascending, setAscending] = useState(group.defaultAscending);
  const [search, setSearch] = useState('');

  const searchTrim = search.trim();
  const effectiveTag = searchTrim ? undefined : group.tag;

  const selectGroup = (id: MarketGroupId) => {
    const g = marketGroupById(id);
    setGroupId(id);
    setOrder(g.defaultOrder);
    setAscending(g.defaultAscending);
  };

  const q = useInfiniteQuery({
    queryKey: ['markets', groupId, order, ascending, searchTrim, effectiveTag],
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      api.listMarkets({
        page: pageParam as number,
        size: 20,
        order,
        ascending,
        tag: effectiveTag,
        search: searchTrim || undefined,
      }),
    getNextPageParam: (last, all) => (last.hasMore ? all.length : undefined),
  });

  return (
    <div className="max-w-full min-w-0 space-y-4">
      <header className="pt-2">
        <h1 className="text-2xl font-semibold">{t('markets.title')}</h1>
      </header>

      <div className="space-y-3">
        <div>
          <p className="mb-1 text-[10px] font-medium uppercase tracking-wide text-tg-hint">{t('markets.topics')}</p>
          <div className="flex max-w-full gap-2 overflow-x-auto pb-1 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
            {MARKET_GROUPS.map((g) => (
              <button
                key={g.id}
                type="button"
                onClick={() => selectGroup(g.id)}
                className={
                  g.id === groupId
                    ? 'shrink-0 rounded-full bg-tg-button px-3 py-1.5 text-xs font-medium text-tg-buttonText'
                    : 'shrink-0 rounded-full bg-tg-secondary px-3 py-1.5 text-xs font-medium text-tg-text'
                }
              >
                {t(`topic.${g.id}`)}
              </button>
            ))}
          </div>
        </div>

        <input
          type="search"
          placeholder={t('markets.search')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full rounded-lg bg-tg-secondary px-3 py-2 text-sm outline-none placeholder:text-tg-hint focus:ring-2 focus:ring-tg-button/40"
        />
        {searchTrim.length > 0 && (
          <p className="text-[11px] text-tg-hint">
            {t('markets.searchHint')}
          </p>
        )}

        <div>
          <p className="mb-1 text-[10px] font-medium uppercase tracking-wide text-tg-hint">{t('markets.sort')}</p>
          <div className="flex max-w-full gap-2 overflow-x-auto pb-1 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
            {ORDERS.map((o) => (
              <button
                key={o.value}
                type="button"
                onClick={() => setOrder(o.value)}
                className={
                  o.value === order
                    ? 'whitespace-nowrap rounded-full bg-tg-button px-3 py-1 text-xs font-medium text-tg-buttonText'
                    : 'whitespace-nowrap rounded-full bg-tg-secondary px-3 py-1 text-xs font-medium text-tg-text'
                }
              >
                {t(o.label)}
              </button>
            ))}
          </div>
        </div>
      </div>

      {q.isLoading && <Spinner label={t('markets.loading')} />}
      {q.error && <ErrorView message={t('markets.loadFailed')} onRetry={() => q.refetch()} />}

      <div className="grid gap-2">
        {q.data?.pages.flatMap((p) => p.items).map((m) => (
          <MarketCard key={m.conditionId} market={m} />
        ))}
      </div>

      {q.hasNextPage && (
        <button
          type="button"
          onClick={() => q.fetchNextPage()}
          disabled={q.isFetchingNextPage}
          className="w-full rounded-lg bg-tg-secondary py-2 text-sm font-medium text-tg-text disabled:opacity-50"
        >
          {q.isFetchingNextPage ? t('common.loading') : t('markets.loadMore')}
        </button>
      )}
    </div>
  );
}
