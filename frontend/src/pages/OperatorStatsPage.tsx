import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '@/lib/api/endpoints';
import { Spinner } from '@/components/Spinner';
import { ErrorView } from '@/components/ErrorView';
import { formatError } from '@/lib/errors';
import { useT } from '@/lib/i18n';
import { readChecklistPreview, writeChecklistPreview } from '@/features/onboarding/firstBet';

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

      <ChecklistPreview />

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
          <div className="grid grid-cols-2 gap-2">
            <Card
              label={t('op.predictions')}
              value={String(stats.data.predictions.total)}
              sub={t('op.predictionsSub', { users: stats.data.predictions.users, resolved: stats.data.predictions.resolved })}
            />
            <Card
              label={t('op.accuracy')}
              value={`${stats.data.predictions.accuracyPct}%`}
              sub={t('op.messagesSent', { n: stats.data.predictions.messagesSent })}
            />
          </div>
          <p className="text-[11px] leading-snug text-tg-hint">{t('op.note')}</p>

          <section className="space-y-2 rounded-xl bg-tg-secondary p-3">
            <h2 className="text-sm font-semibold text-tg-text">{t('op.funnel')}</h2>
            {(
              [
                ['op.f.opened', stats.data.funnel.opened],
                ['op.f.predicted', stats.data.funnel.predicted],
                ['op.f.wallet', stats.data.funnel.wallet],
                ['op.f.tried', stats.data.funnel.tried],
                ['op.f.bet', stats.data.funnel.bet],
              ] as const
            ).map(([key, n]) => {
              const top = Math.max(1, stats.data.funnel.opened);
              return (
                <div key={key}>
                  <div className="flex justify-between text-xs text-tg-text">
                    <span>{t(key)}</span>
                    <span className="tabular-nums">
                      {n} <span className="text-tg-hint">· {Math.round((n / top) * 100)}%</span>
                    </span>
                  </div>
                  <div className="mt-0.5 h-1.5 overflow-hidden rounded-full bg-tg-bg">
                    <div className="h-full bg-tg-button" style={{ width: `${(n / top) * 100}%` }} />
                  </div>
                </div>
              );
            })}
          </section>

          <section className="space-y-1 rounded-xl bg-tg-secondary p-3">
            <h2 className="text-sm font-semibold text-tg-text">{t('op.sources')}</h2>
            <p className="text-[11px] text-tg-hint">{t('op.sourcesHint')}</p>
            <table className="w-full text-xs tabular-nums">
              <thead className="text-tg-hint">
                <tr>
                  <th className="py-1 text-left font-medium">{t('op.s.source')}</th>
                  <th className="text-right font-medium">{t('op.s.launches')}</th>
                  <th className="text-right font-medium">{t('op.s.users')}</th>
                  <th className="text-right font-medium">{t('op.s.wallet')}</th>
                  <th className="text-right font-medium">{t('op.s.bet')}</th>
                </tr>
              </thead>
              <tbody className="text-tg-text">
                {stats.data.sources.map((r) => (
                  <tr key={r.source} className="border-t border-tg-hint/10">
                    <td className="py-1 font-mono">{r.source === 'direct' ? t('op.s.direct') : r.source}</td>
                    <td className="text-right">{r.launches}</td>
                    <td className="text-right">{r.users}</td>
                    <td className="text-right">{r.wallet}</td>
                    <td className="text-right">{r.bet}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        </>
      )}
    </div>
  );
}

/** Lets the operator see the first-bet checklist as a new user would, at any step. */
function ChecklistPreview() {
  const t = useT();
  const [value, setValue] = useState(readChecklistPreview);
  const set = (v: number | null) => {
    writeChecklistPreview(v);
    setValue(v);
  };
  return (
    <section className="space-y-2 rounded-xl bg-tg-secondary p-3">
      <div className="flex items-center justify-between gap-2">
        <div>
          <h2 className="text-sm font-semibold text-tg-text">{t('op.preview')}</h2>
          <p className="text-[11px] text-tg-hint">{t('op.previewHint')}</p>
        </div>
        <button
          type="button"
          role="switch"
          aria-checked={value != null}
          onClick={() => set(value == null ? 0 : null)}
          className={
            value != null
              ? 'shrink-0 rounded-full bg-tg-button px-3 py-1 text-xs font-medium text-tg-buttonText'
              : 'shrink-0 rounded-full bg-tg-bg px-3 py-1 text-xs font-medium text-tg-hint'
          }
        >
          {value != null ? t('common.on') : t('common.off')}
        </button>
      </div>
      {value != null && (
        <div className="flex items-center gap-1.5">
          <span className="text-[11px] text-tg-hint">{t('op.previewDone')}</span>
          {[0, 1, 2, 3, 4].map((n) => (
            <button
              key={n}
              type="button"
              onClick={() => set(n)}
              className={
                n === value
                  ? 'h-7 w-7 rounded-md bg-tg-button text-xs font-semibold text-tg-buttonText'
                  : 'h-7 w-7 rounded-md bg-tg-bg text-xs text-tg-text'
              }
            >
              {n}
            </button>
          ))}
        </div>
      )}
    </section>
  );
}
