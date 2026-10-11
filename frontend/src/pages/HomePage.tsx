import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api/endpoints';
import { MarketCard } from '@/components/MarketCard';
import { Spinner } from '@/components/Spinner';
import { ErrorView } from '@/components/ErrorView';
import { useAuthStore } from '@/features/auth/store';
import { LanguagePicker } from '@/components/LanguagePicker';
import { useT } from '@/lib/i18n';
import { FirstBetChecklist } from '@/features/onboarding/FirstBetChecklist';
import { isWalletConfigured } from '@/features/wallet/useWallet';

export function HomePage() {
  const user = useAuthStore((s) => s.user);
  const t = useT();
  const featured = useQuery({
    queryKey: ['featured'],
    queryFn: () => api.listMarkets({ size: 8, order: 'volume24hr', ascending: false }),
  });

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

      {isWalletConfigured() && <FirstBetChecklist />}

      {featured.isLoading && <Spinner label={t('markets.loading')} />}
      {featured.error && (
        <ErrorView message={t('markets.loadFailed')} onRetry={() => featured.refetch()} />
      )}

      <div className="grid gap-2">
        {featured.data?.items.map((m) => (
          <MarketCard key={m.conditionId} market={m} />
        ))}
      </div>
    </div>
  );
}
