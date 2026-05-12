import { useQueries, useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api/endpoints';
import { MarketCard } from '@/components/MarketCard';
import { Spinner } from '@/components/Spinner';

export function FavoritesPage() {
  const favs = useQuery({ queryKey: ['favorites'], queryFn: api.listFavorites });

  const marketQueries = useQueries({
    queries:
      favs.data?.map((f) => ({
        queryKey: ['market', f.conditionId],
        queryFn: () => api.getMarket(f.conditionId),
      })) ?? [],
  });

  return (
    <div className="space-y-4">
      <header className="pt-2">
        <h1 className="text-2xl font-semibold">Saved</h1>
      </header>

      {favs.isLoading && <Spinner />}
      {favs.data && favs.data.length === 0 && (
        <p className="rounded-xl bg-tg-secondary p-4 text-center text-sm text-tg-hint">
          No saved markets yet. Tap ★ on a market to keep it here.
        </p>
      )}

      <div className="grid gap-2">
        {marketQueries
          .filter((q): q is typeof q & { data: NonNullable<typeof q.data> } => !!q.data)
          .map((q) => (
            <MarketCard key={q.data.conditionId} market={q.data} />
          ))}
      </div>
    </div>
  );
}
