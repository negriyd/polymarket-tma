import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import clsx from 'clsx';
import { api } from '@/lib/api/endpoints';
import { Spinner } from '@/components/Spinner';
import { ErrorView } from '@/components/ErrorView';
import { formatError } from '@/lib/errors';
import { useLocale, useT } from '@/lib/i18n';
import { LeaderboardTable } from '@/features/partners/LeaderboardTable';

/** "Top forecaster of the week" among the users a partner brought. Public, with Telegram names. */
export function LeaderboardPage() {
  const t = useT();
  const locale = useLocale();
  const { code = '' } = useParams();
  const [week, setWeek] = useState<'current' | 'previous'>('current');
  const board = useQuery({
    queryKey: ['leaderboard', code, week],
    queryFn: () => api.getLeaderboard(code, week),
    refetchInterval: 5 * 60_000,
  });
  const b = board.data;
  const range = (from: string, to: string) => {
    const end = new Date(new Date(to).getTime() - 1);
    const fmt = (d: Date) => d.toLocaleDateString(locale, { day: 'numeric', month: 'short', timeZone: 'UTC' });
    return `${fmt(new Date(from))} – ${fmt(end)}`;
  };

  return (
    <div className="max-w-full min-w-0 space-y-4">
      <Link to="/" className="inline-flex items-center text-sm text-tg-hint">
        {t('common.back')}
      </Link>
      <div>
        <h1 className="text-2xl font-semibold">🏆 {t('lb.title')}</h1>
        {b && <p className="text-sm text-tg-hint">{b.author}</p>}
      </div>

      <div className="flex gap-1">
        {(['current', 'previous'] as const).map((w) => (
          <button
            key={w}
            type="button"
            onClick={() => setWeek(w)}
            className={clsx(
              'rounded-full px-3 py-1 text-xs font-medium',
              w === week ? 'bg-tg-button text-tg-buttonText' : 'bg-tg-secondary text-tg-hint',
            )}
          >
            {t(w === 'current' ? 'lb.thisWeek' : 'lb.lastWeek')}
          </button>
        ))}
      </div>

      {board.isLoading && <Spinner />}
      {board.isError && <ErrorView message={formatError(board.error)} onRetry={() => board.refetch()} />}
      {b && (
        <section className="space-y-2 rounded-xl bg-tg-secondary p-3">
          <p className="text-[11px] text-tg-hint">
            {range(b.weekStart, b.weekEnd)} · {t('lb.rules', { n: b.minResolved })}
          </p>
          <LeaderboardTable board={b} />
        </section>
      )}
      <p className="text-[11px] leading-snug text-tg-hint">{t('lb.how')}</p>
      <Link
        to="/markets"
        className="block w-full rounded-xl bg-tg-button py-2.5 text-center text-sm font-semibold text-tg-buttonText"
      >
        {t('lb.cta')}
      </Link>
    </div>
  );
}
