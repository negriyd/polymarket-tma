import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '@/lib/api/endpoints';
import { outcomeText, useT } from '@/lib/i18n';
import { authorRef } from './authorRef';

/**
 * The partner whose link brought the user (or who invited them): their public forecast accuracy,
 * their own pick on this market, and a link to the weekly leaderboard of their audience.
 */
export function AuthorBadge({ conditionId }: { conditionId?: string }) {
  const t = useT();
  const code = authorRef();
  const author = useQuery({
    queryKey: ['author', code, conditionId ?? null],
    queryFn: () => api.getCurrentAuthor(code, conditionId),
    staleTime: 5 * 60_000,
    retry: false,
  });
  const a = author.data;
  if (!a) return null;

  const pick = a.pick;
  return (
    <section className="space-y-1 rounded-xl bg-amber-400/10 px-3 py-2.5 ring-1 ring-amber-400/30">
      <div className="flex items-center justify-between gap-2">
        <p className="min-w-0 truncate text-sm font-semibold text-tg-text">📣 {a.name}</p>
        <Link to={`/leaderboard/${a.code}`} className="shrink-0 text-xs font-medium text-tg-link">
          {t('author.leaderboard')} ›
        </Link>
      </div>
      <p className="text-xs text-tg-hint">
        {a.accuracy.pct != null
          ? t('author.accuracy', { pct: a.accuracy.pct, correct: a.accuracy.correct, resolved: a.accuracy.resolved })
          : t('author.accuracyPending')}
      </p>
      {pick && (
        <p className="text-xs text-tg-text">
          {t('author.pick', { outcome: outcomeText(pick.outcome, t) })}
          {pick.price != null && <span className="text-tg-hint"> · {Math.round(pick.price * 100)}¢</span>}
          {pick.resolved && (
            <span className={pick.correct ? 'text-emerald-500' : 'text-rose-500'}>
              {' '}
              · {pick.correct ? t('author.pickRight') : t('author.pickWrong')}
            </span>
          )}
        </p>
      )}
    </section>
  );
}
