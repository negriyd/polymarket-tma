import { Link } from 'react-router-dom';
import clsx from 'clsx';
import type { Market } from '@/lib/api/types';
import { hapticImpact } from '@/lib/telegram/webApp';

interface Props {
  market: Market;
  compact?: boolean;
}

function formatVolume(value?: number): string {
  if (value == null) return '—';
  if (value >= 1_000_000) return `$${(value / 1_000_000).toFixed(1)}M`;
  if (value >= 1_000) return `$${(value / 1_000).toFixed(1)}K`;
  return `$${value.toFixed(0)}`;
}

function pickYesPrice(market: Market): number | null {
  if (!market.outcomePrices?.length) return null;
  if (market.outcomes?.length) {
    const yesIdx = market.outcomes.findIndex((o) => o.toLowerCase() === 'yes');
    if (yesIdx >= 0) {
      const raw = market.outcomePrices[yesIdx];
      return raw == null ? null : Number(raw);
    }
  }
  const raw = market.outcomePrices[0];
  return raw == null ? null : Number(raw);
}

export function MarketCard({ market, compact }: Props) {
  const yesPrice = pickYesPrice(market);
  const yesPct = yesPrice == null ? null : Math.round(yesPrice * 100);

  return (
    <Link
      to={`/markets/${market.id ?? market.conditionId}`}
      onClick={() => hapticImpact('light')}
      className="block"
    >
      <article
        className={clsx(
          'flex gap-3 rounded-xl bg-tg-secondary p-3 transition active:scale-[0.99]',
          compact ? 'items-center' : 'items-start',
        )}
      >
        {market.image && (
          <img
            src={market.image}
            alt=""
            className={clsx('shrink-0 rounded-lg object-cover', compact ? 'h-10 w-10' : 'h-14 w-14')}
            loading="lazy"
          />
        )}
        <div className="min-w-0 flex-1">
          <h3 className="truncate text-sm font-medium text-tg-text">{market.question}</h3>
          <p className="mt-1 text-xs text-tg-hint">
            Vol {formatVolume(market.volume24h ?? market.volume)} · Liq {formatVolume(market.liquidity)}
          </p>
        </div>
        {yesPct != null && (
          <div className="text-right">
            <div
              className={clsx(
                'rounded-md px-2 py-1 text-sm font-semibold tabular-nums',
                yesPct >= 50 ? 'bg-emerald-500/15 text-emerald-500' : 'bg-rose-500/15 text-rose-500',
              )}
            >
              {yesPct}%
            </div>
            <div className="mt-0.5 text-[10px] uppercase tracking-wide text-tg-hint">Yes</div>
          </div>
        )}
      </article>
    </Link>
  );
}
