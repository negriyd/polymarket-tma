import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api/endpoints';
import { MarketCard } from '@/components/MarketCard';
import { Spinner } from '@/components/Spinner';
import { ErrorView } from '@/components/ErrorView';
import { useAuthStore } from '@/features/auth/store';
import { LanguagePicker } from '@/components/LanguagePicker';
import { useLocale, useT } from '@/lib/i18n';
import { FirstBetChecklist } from '@/features/onboarding/FirstBetChecklist';
import { isWalletConfigured } from '@/features/wallet/useWallet';
import { AuthorBadge } from '@/features/partners/AuthorBadge';
import { RiskNote } from '@/components/RiskNote';
import { TopUpStatus } from '@/features/wallet/TopUpStatus';
import { PaperProfitNudge } from '@/features/predictions/PaperProfitNudge';

export function HomePage() {
  const user = useAuthStore((s) => s.user);
  const t = useT();
  const locale = useLocale();
  const featured = useQuery({
    queryKey: ['featured'],
    queryFn: () => api.listMarkets({ size: 8, order: 'volume24hr', ascending: false }),
  });
  const local = useQuery({
    queryKey: ['local-markets', locale],
    queryFn: api.listLocalMarkets,
    staleTime: 60_000,
  });
  const trendingIds = new Set(featured.data?.items.map((m) => m.conditionId));
  const localItems = (local.data ?? []).filter((m) => !trendingIds.has(m.conditionId)).slice(0, 4);

  return (
    <div className="max-w-full min-w-0 space-y-5">
      <header className="flex items-start justify-between gap-2 pt-2">
        <div className="min-w-0">
          <p className="text-sm text-tg-hint">
            {t('home.greeting', { name: user?.firstName ?? user?.username ?? t('home.greetingFallback') })}
          </p>
          <h1 className="text-2xl font-semibold">{t('home.trending')}</h1>
        </div>
        <LanguagePicker />
      </header>

      <AuthorBadge />

      {isWalletConfigured() && <TopUpStatus />}
      {isWalletConfigured() && <FirstBetChecklist />}
      <PaperProfitNudge />

      {featured.isLoading && <Spinner label={t('markets.loading')} />}
      {featured.error && (
        <ErrorView message={t('markets.loadFailed')} onRetry={() => featured.refetch()} />
      )}

      <div className="grid gap-2">
        {featured.data?.items.map((m) => (
          <MarketCard key={m.conditionId} market={m} />
        ))}
      </div>

      {localItems.length > 0 && (
        <section className="space-y-2">
          <h2 className="text-lg font-semibold">{t('home.local')}</h2>
          <div className="grid gap-2">
            {localItems.map((m) => (
              <MarketCard key={m.conditionId} market={m} />
            ))}
          </div>
        </section>
      )}

      <RiskNote />
    </div>
  );
}
