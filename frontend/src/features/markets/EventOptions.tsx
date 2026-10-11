import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import clsx from 'clsx';
import type { Market, MarketEvent, SeriesEvent } from '@/lib/api/types';
import { useLocale, useT } from '@/lib/i18n';
import { hapticImpact } from '@/lib/telegram/webApp';

interface Props {
  event: MarketEvent;
  currentConditionId: string;
  onTrade: (market: Market, side: 'yes' | 'no') => void;
}

function yesNoPrices(m: Market): { yes?: number; no?: number } {
  const outcomes = (m.outcomes ?? []).map((o) => o.toLowerCase());
  const at = (i: number) => (i >= 0 && m.outcomePrices?.[i] != null ? Number(m.outcomePrices[i]) : undefined);
  const yesIdx = outcomes.indexOf('yes');
  const noIdx = outcomes.indexOf('no');
  return { yes: at(yesIdx >= 0 ? yesIdx : 0), no: at(noIdx >= 0 ? noIdx : 1) };
}

const cents = (p?: number) => (p == null ? '—' : `${(p * 100).toFixed(p < 0.1 || p > 0.9 ? 1 : 0)}¢`);

function formatVolume(v?: number): string {
  if (v == null) return '—';
  if (v >= 1_000_000) return `$${(v / 1_000_000).toFixed(1)}M`;
  if (v >= 1_000) return `$${(v / 1_000).toFixed(0)}K`;
  return `$${v.toFixed(0)}`;
}

/** Polymarket dates its events in US Eastern time ("Nov 3" for 04:59 UTC Nov 4). */
function chipLabel(e: SeriesEvent, locale: string): string {
  if (!e.endDate) return e.title;
  return new Date(e.endDate).toLocaleDateString(locale, {
    month: 'short',
    day: 'numeric',
    timeZone: 'America/New_York',
  });
}

function winner(m: Market): 'yes' | 'no' | null {
  const prices = (m.outcomePrices ?? []).map(Number);
  const outcomes = (m.outcomes ?? []).map((o) => o.toLowerCase());
  const won = prices.findIndex((p) => p >= 0.99);
  if (won < 0) return null;
  return outcomes[won] === 'no' || (outcomes.length === 0 && won === 1) ? 'no' : 'yes';
}

/** Other events of the same series (earlier / later months), like the date chips on polymarket.com. */
function SeriesChips({ events }: { events: SeriesEvent[] }) {
  const t = useT();
  const locale = useLocale();
  const navigate = useNavigate();
  const [showPast, setShowPast] = useState(false);
  const past = events.filter((e) => e.closed && !e.current);
  const upcoming = events.filter((e) => !e.closed || e.current);

  const chip = (e: SeriesEvent) => (
    <button
      key={e.id}
      type="button"
      disabled={e.current}
      onClick={() => {
        if (!e.marketKey) return;
        hapticImpact('light');
        navigate(`/markets/${e.marketKey}`, { replace: true });
      }}
      className={clsx(
        'shrink-0 rounded-full px-3 py-1 text-xs font-medium',
        e.current ? 'bg-tg-button text-tg-buttonText' : 'bg-tg-secondary text-tg-text',
      )}
    >
      {chipLabel(e, locale)}
    </button>
  );

  return (
    <div className="flex max-w-full gap-1.5 overflow-x-auto pb-1 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
      {past.length > 0 && (
        <button
          type="button"
          onClick={() => setShowPast((v) => !v)}
          className="shrink-0 rounded-full bg-tg-secondary px-3 py-1 text-xs font-medium text-tg-text"
        >
          {t('market.past')} {showPast ? '▴' : '▾'}
        </button>
      )}
      {showPast && past.map(chip)}
      {upcoming.map(chip)}
    </div>
  );
}

/**
 * The other markets of a multi-market event — the dates of a "…by?" ladder or the candidates of a
 * race — so the user can pick which one to trade, like the date list on polymarket.com.
 */
export function EventOptions({ event, currentConditionId, onTrade }: Props) {
  const t = useT();
  const [showResolved, setShowResolved] = useState(false);
  const series = event.seriesEvents ?? [];
  const resolved = event.resolved ?? [];
  return (
    <section className="space-y-1.5">
      {series.length > 1 && <SeriesChips events={series} />}
      {event.markets.length > 0 && (
        <div>
          <h2 className="text-sm font-semibold text-tg-text">{t('market.options')}</h2>
          <p className="text-[11px] text-tg-hint">{t('market.optionsHint')}</p>
        </div>
      )}
      <ul className="space-y-1.5">
        {event.markets.map((m) => {
          const { yes, no } = yesNoPrices(m);
          const current = m.conditionId.toLowerCase() === currentConditionId.toLowerCase();
          const pct = yes == null ? null : Math.round(yes * 100);
          return (
            <li
              key={m.conditionId}
              className={clsx(
                'flex items-center gap-2 rounded-xl bg-tg-secondary px-3 py-2',
                current && 'ring-2 ring-tg-button',
              )}
            >
              <Link
                to={`/markets/${m.id ?? m.conditionId}`}
                replace
                onClick={() => hapticImpact('light')}
                className="min-w-0 flex-1"
              >
                <p className="truncate text-sm font-medium text-tg-text">{m.groupItemTitle || m.question}</p>
                <p className="text-[10px] text-tg-hint">
                  {t('market.optionVolume', { vol: formatVolume(m.volume) })}
                </p>
              </Link>
              <span className="w-10 shrink-0 text-right text-base font-semibold tabular-nums text-tg-text">
                {pct == null ? '—' : `${pct}%`}
              </span>
              <button
                type="button"
                onClick={() => onTrade(m, 'yes')}
                className="shrink-0 rounded-lg bg-emerald-500/15 px-2 py-1.5 text-xs font-semibold tabular-nums text-emerald-500"
              >
                {t('common.yes')} {cents(yes)}
              </button>
              <button
                type="button"
                onClick={() => onTrade(m, 'no')}
                className="shrink-0 rounded-lg bg-rose-500/15 px-2 py-1.5 text-xs font-semibold tabular-nums text-rose-500"
              >
                {t('common.no')} {cents(no)}
              </button>
            </li>
          );
        })}
      </ul>
      {resolved.length > 0 && (
        <>
          <button
            type="button"
            onClick={() => setShowResolved((v) => !v)}
            className="px-1 text-xs font-medium text-tg-hint"
          >
            {showResolved ? t('market.hideResolved') : t('market.viewResolved', { count: resolved.length })}{' '}
            {showResolved ? '▴' : '▾'}
          </button>
          {showResolved && (
            <ul className="space-y-1.5">
              {resolved.map((m) => {
                const w = winner(m);
                const current = m.conditionId.toLowerCase() === currentConditionId.toLowerCase();
                return (
                  <li key={m.conditionId}>
                    <Link
                      to={`/markets/${m.id ?? m.conditionId}`}
                      replace
                      className={clsx(
                        'flex items-center justify-between gap-2 rounded-xl bg-tg-secondary/60 px-3 py-2',
                        current && 'ring-2 ring-tg-button',
                      )}
                    >
                      <span className="min-w-0 truncate text-sm text-tg-hint">{m.groupItemTitle || m.question}</span>
                      <span
                        className={clsx(
                          'shrink-0 rounded-md px-1.5 py-0.5 text-[11px] font-semibold',
                          w === 'yes' && 'bg-emerald-500/15 text-emerald-500',
                          w === 'no' && 'bg-rose-500/15 text-rose-500',
                          w == null && 'bg-tg-bg text-tg-hint',
                        )}
                      >
                        {w == null
                          ? t('market.resolving')
                          : t('market.resolvedTo', { outcome: t(w === 'yes' ? 'common.yes' : 'common.no') })}
                      </span>
                    </Link>
                  </li>
                );
              })}
            </ul>
          )}
        </>
      )}
    </section>
  );
}
