import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api/endpoints';
import { MarketCard } from '@/components/MarketCard';
import { Spinner } from '@/components/Spinner';
import { ErrorView } from '@/components/ErrorView';
import { useAuthStore } from '@/features/auth/store';

export function HomePage() {
  const user = useAuthStore((s) => s.user);
  const featured = useQuery({
    queryKey: ['featured'],
    queryFn: () => api.listMarkets({ size: 8, order: 'volume24hr', ascending: false }),
  });

  return (
    <div className="max-w-full min-w-0 space-y-5">
      <header className="pt-2">
        <p className="text-sm text-tg-hint">Hi {user?.firstName ?? user?.username ?? 'there'}!</p>
        <h1 className="text-2xl font-semibold">Trending markets</h1>
      </header>

      {featured.isLoading && <Spinner label="Loading markets…" />}
      {featured.error && (
        <ErrorView message="Failed to load markets" onRetry={() => featured.refetch()} />
      )}

      <div className="grid gap-2">
        {featured.data?.items.map((m) => (
          <MarketCard key={m.conditionId} market={m} />
        ))}
      </div>
    </div>
  );
}
