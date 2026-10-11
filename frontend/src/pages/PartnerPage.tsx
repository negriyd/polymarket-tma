import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { Link } from 'react-router-dom';
import { api } from '@/lib/api/endpoints';
import { Spinner } from '@/components/Spinner';
import { ErrorView } from '@/components/ErrorView';
import { formatError } from '@/lib/errors';
import { useLocale, useT } from '@/lib/i18n';
import { PartnerStats } from '@/features/partners/PartnerStats';
import { cleanTag, taggedLink, usd } from '@/features/partners/partnerLinks';
import { LeaderboardTable } from '@/features/partners/LeaderboardTable';
import type { Leaderboard } from '@/lib/api/types';

/** Partner cabinet: terms, link generator with tags, and the funnel per tag. */
export function PartnerPage() {
  const t = useT();
  const locale = useLocale();
  const [tag, setTag] = useState('');
  const [copied, setCopied] = useState(false);
  const me = useQuery({ queryKey: ['partner-me'], queryFn: api.getPartnerCabinet, refetchInterval: 60_000 });

  const c = me.data?.cabinet;
  const link = me.data ? taggedLink(me.data.link, tag) : '';
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link);
      setCopied(true);
      setTimeout(() => setCopied(false), 1_500);
    } catch {
      /* clipboard may be unavailable inside some Telegram clients */
    }
  };

  return (
    <div className="max-w-full min-w-0 space-y-4">
      <Link to="/wallet" className="inline-flex items-center text-sm text-tg-hint">
        {t('common.back')}
      </Link>
      <h1 className="text-2xl font-semibold">{t('pt.open')}</h1>

      {me.isLoading && <Spinner />}
      {me.isError && <ErrorView message={formatError(me.error)} onRetry={() => me.refetch()} />}

      {c && (
        <>
          <section className="space-y-2 rounded-xl bg-tg-secondary p-3">
            <p className="text-xs leading-snug text-tg-text">
              {c.months == null
                ? t('pt.termsLifetime', { pct: c.shareBps / 100 })
                : t('pt.terms', { pct: c.shareBps / 100, months: c.months })}
            </p>
            <p className="text-[11px] text-tg-hint">
              {t('pt.since', { date: new Date(c.since).toLocaleDateString(locale) })}
            </p>
            <PartnerStats totals={c.totals} />
          </section>

          <section className="space-y-1 rounded-xl bg-tg-secondary p-3">
            <h2 className="text-sm font-semibold text-tg-text">{t('pt.accuracyTitle')}</h2>
            <p className="text-xs text-tg-text">
              {c.accuracy.pct != null
                ? t('author.accuracy', {
                    pct: c.accuracy.pct,
                    correct: c.accuracy.correct,
                    resolved: c.accuracy.resolved,
                  })
                : t('pt.accuracyPending', { n: c.accuracy.resolved, min: 5 })}
            </p>
            <p className="text-[11px] leading-snug text-tg-hint">{t('pt.accuracyHint', { name: c.publicName })}</p>
          </section>

          <WeeklyBoard code={c.code} link={me.data!.link} />

          <section className="space-y-2 rounded-xl bg-tg-secondary p-3">
            <h2 className="text-sm font-semibold text-tg-text">{t('pt.links')}</h2>
            <p className="text-[11px] leading-snug text-tg-hint">{t('pt.linksHint')}</p>
            <input
              className="w-full rounded-lg bg-tg-bg px-3 py-2 font-mono text-sm text-tg-text outline-none placeholder:font-sans placeholder:text-tg-hint"
              placeholder={t('pt.tagPlaceholder')}
              value={tag}
              maxLength={40}
              onChange={(e) => setTag(e.target.value)}
              onBlur={() => setTag(cleanTag(tag))}
            />
            <button type="button" onClick={() => void copy()} className="w-full rounded-lg bg-tg-bg p-2 text-left">
              <span className="block text-[10px] uppercase tracking-wide text-tg-hint">
                {copied ? t('common.copied') : t('common.tapToCopy')}
              </span>
              <span className="block break-all font-mono text-xs text-tg-text">{link}</span>
            </button>
          </section>

          <section className="space-y-1 rounded-xl bg-tg-secondary p-3">
            <h2 className="text-sm font-semibold text-tg-text">{t('pt.bySource')}</h2>
            {c.sources.length === 0 ? (
              <p className="text-xs text-tg-hint">{t('pt.empty')}</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-xs tabular-nums">
                  <thead className="text-tg-hint">
                    <tr>
                      <th className="py-1 text-left font-medium">{t('op.s.source')}</th>
                      <th className="text-right font-medium">{t('pt.opens')}</th>
                      <th className="text-right font-medium">{t('pt.users')}</th>
                      <th className="text-right font-medium">{t('pt.bettors')}</th>
                      <th className="text-right font-medium">{t('pt.volume')}</th>
                      <th className="text-right font-medium">{t('pt.earned')}</th>
                    </tr>
                  </thead>
                  <tbody className="text-tg-text">
                    {c.sources.map((r) => (
                      <tr key={r.source ?? ''} className="border-t border-tg-hint/10">
                        <td className="py-1 font-mono">{r.source ?? t('pt.noTag')}</td>
                        <td className="text-right">{r.launches}</td>
                        <td className="text-right">{r.users}</td>
                        <td className="text-right">{r.bet}</td>
                        <td className="text-right">{usd(r.volumeUsdc)}</td>
                        <td className="text-right">{usd(r.earnedUsdc)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
          <p className="text-[11px] leading-snug text-tg-hint">{t('pt.note')}</p>
        </>
      )}
    </div>
  );
}

/** The audience's weekly leaderboard with a ready-to-post text. */
function WeeklyBoard({ code, link }: { code: string; link: string }) {
  const t = useT();
  const [week, setWeek] = useState<'current' | 'previous'>('previous');
  const [copied, setCopied] = useState(false);
  const board = useQuery({ queryKey: ['leaderboard', code, week], queryFn: () => api.getLeaderboard(code, week) });

  const post = (b: Leaderboard) => {
    const medals = ['🥇', '🥈', '🥉'];
    const rows = b.top
      .slice(0, 5)
      .map((r) => `${medals[r.rank - 1] ?? `${r.rank}.`} ${r.name} — ${t('lb.score', { correct: r.correct, resolved: r.resolved })}`);
    return [t('pt.postTitle', { name: b.author }), '', ...rows, '', t('pt.postCta'), `${link}_lb`].join('\n');
  };
  const copy = async (b: Leaderboard) => {
    try {
      await navigator.clipboard.writeText(post(b));
      setCopied(true);
      setTimeout(() => setCopied(false), 1_500);
    } catch {
      /* clipboard may be unavailable inside some Telegram clients */
    }
  };

  return (
    <section className="space-y-2 rounded-xl bg-tg-secondary p-3">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-tg-text">🏆 {t('lb.title')}</h2>
        <div className="flex gap-1">
          {(['previous', 'current'] as const).map((w) => (
            <button
              key={w}
              type="button"
              onClick={() => setWeek(w)}
              className={clsx(
                'rounded-full px-2 py-0.5 text-[11px] font-medium',
                w === week ? 'bg-tg-button text-tg-buttonText' : 'bg-tg-bg text-tg-hint',
              )}
            >
              {t(w === 'current' ? 'lb.thisWeek' : 'lb.lastWeek')}
            </button>
          ))}
        </div>
      </div>
      <p className="text-[11px] leading-snug text-tg-hint">{t('pt.boardHint')}</p>
      {board.data && <LeaderboardTable board={board.data} />}
      {board.data && board.data.top.length > 0 && (
        <button
          type="button"
          onClick={() => void copy(board.data)}
          className="w-full rounded-lg bg-tg-button py-2 text-sm font-semibold text-tg-buttonText"
        >
          {copied ? t('common.copied') : t('pt.copyPost')}
        </button>
      )}
      <p className="break-all font-mono text-[11px] text-tg-hint">{link}_lb</p>
    </section>
  );
}
