import { Link } from 'react-router-dom';
import clsx from 'clsx';
import type { Market, MarketEvent } from '@/lib/api/types';
import { useT } from '@/lib/i18n';
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

/**
 * The other markets of a multi-market event — the dates of a "…by?" ladder or the candidates of a
 * race — so the user can pick which one to trade, like the date list on polymarket.com.
 */
export function EventOptions({ event, currentConditionId, onTrade }: Props) {
  const t = useT();
  return (
    <section className="space-y-1.5">
      <div>
        <h2 className="text-sm font-semibold text-tg-text">{t('market.options')}</h2>
        <p className="text-[11px] text-tg-hint">{t('market.optionsHint')}</p>
      </div>
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
    </section>
  );
}
