import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '@/lib/api/endpoints';
import { Spinner } from '@/components/Spinner';
import { ErrorView } from '@/components/ErrorView';
import { formatError } from '@/lib/errors';
import { useT } from '@/lib/i18n';

function Card({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="rounded-xl bg-tg-secondary px-3 py-3">
      <p className="text-[10px] uppercase tracking-wide text-tg-hint">{label}</p>
      <p className="mt-0.5 text-2xl font-semibold tabular-nums text-tg-text">{value}</p>
      {sub && <p className="mt-0.5 text-[11px] text-tg-hint">{sub}</p>}
    </div>
  );
}

const usd = (v: string) => `$${Number(v).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

/** Platform totals for the operator (the owner of the fee-receiving wallet). */
export function OperatorStatsPage() {
  const t = useT();
  const stats = useQuery({ queryKey: ['operator-stats'], queryFn: api.getOperatorStats, refetchInterval: 60_000 });

  return (
    <div className="max-w-full min-w-0 space-y-4">
      <Link to="/wallet" className="inline-flex items-center text-sm text-tg-hint">
        {t('common.back')}
      </Link>
      <h1 className="text-2xl font-semibold">{t('op.title')}</h1>

      {stats.isLoading && <Spinner />}
      {stats.isError && <ErrorView message={formatError(stats.error)} onRetry={() => stats.refetch()} />}

      {stats.data && (
        <>
          <div className="grid grid-cols-2 gap-2">
            <Card
              label={t('op.users')}
              value={String(stats.data.users)}
              sub={t('op.usersSub', { n: stats.data.usersLast7d, w: stats.data.usersWithWallet })}
            />
            <Card label={t('op.bets')} value={String(stats.data.bets)} />
            <Card label={t('op.volume')} value={usd(stats.data.volumeUsdc)} />
            <Card label={t('op.avg')} value={usd(stats.data.avgBetUsdc)} />
            <Card
              label={t('op.fees')}
              value={usd(stats.data.feesUsdc)}
              sub={t('op.feesSub', { n: stats.data.feePayments })}
            />
            <Card
              label={t('op.referrals')}
              value={usd(stats.data.referralUsdc)}
              sub={t('op.refSub', { n: stats.data.referredUsers })}
            />
          </div>
          <p className="text-[11px] leading-snug text-tg-hint">{t('op.note')}</p>
        </>
      )}
    </div>
  );
}
