import { Link } from 'react-router-dom';
import type { Position } from '@/lib/api/types';

interface Props {
  p: Position;
}

function fmtUsd(n: number): string {
  const abs = Math.abs(n);
  const fmt = abs >= 1000 ? n.toFixed(0) : n.toFixed(2);
  return `$${fmt}`;
}

function fmtPercent(frac: number): string {
  const pct = frac * 100;
  const sign = pct >= 0 ? '+' : '';
  return `${sign}${pct.toFixed(1)}%`;
}

function fmtShares(n: number): string {
  if (Math.abs(n) >= 100) return n.toFixed(0);
  if (Math.abs(n) >= 1) return n.toFixed(2);
  return n.toFixed(4);
}

/**
 * One open position. Shows the market title, outcome label colored by Yes/No, share amount and avg
 * price, current value and unrealised PnL. {@link Position.redeemable} swaps the CTA so the user can
 * jump straight to the (future) Claim flow once the market is settled.
 */
export function PositionRow({ p }: Props) {
  const yes = p.outcome?.toLowerCase() === 'yes';
  const outcomeColor = yes ? 'bg-emerald-500/15 text-emerald-500' : 'bg-rose-500/15 text-rose-500';
  const pnlColor = p.cashPnl >= 0 ? 'text-emerald-500' : 'text-rose-500';

  return (
    <li className="rounded-xl bg-tg-secondary p-3">
      <div className="flex items-start gap-3">
        {p.icon ? (
          <img
            src={p.icon}
            alt=""
            className="h-9 w-9 shrink-0 rounded-md object-cover"
            loading="lazy"
            draggable={false}
          />
        ) : (
          <div className="h-9 w-9 shrink-0 rounded-md bg-tg-bg" />
        )}
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5">
            <span className={`rounded-md px-1.5 py-0.5 text-[10px] font-bold uppercase ${outcomeColor}`}>
              {p.outcome ?? '—'}
            </span>
            {p.favorite && (
              <span className="rounded-md bg-amber-500/15 px-1.5 py-0.5 text-[10px] font-medium text-amber-500">
                ★ favorite
              </span>
            )}
            {p.redeemable && (
              <span className="rounded-md bg-tg-button/20 px-1.5 py-0.5 text-[10px] font-medium text-tg-button">
                redeemable
              </span>
            )}
          </div>
          <Link
            to={`/markets/${encodeURIComponent(p.conditionId)}`}
            className="mt-0.5 block truncate text-sm font-medium text-tg-text hover:underline"
            title={p.title}
          >
            {p.title || p.conditionId.slice(0, 14) + '…'}
          </Link>
          <p className="text-[11px] text-tg-hint">
            {fmtShares(p.size)} sh @ {p.avgPrice.toFixed(3)} · now {p.curPrice.toFixed(3)}
          </p>
        </div>
        <div className="shrink-0 text-right">
          <p className="text-sm font-semibold tabular-nums text-tg-text">{fmtUsd(p.currentValue)}</p>
          <p className={`text-[11px] tabular-nums ${pnlColor}`}>
            {p.cashPnl >= 0 ? '+' : ''}
            {fmtUsd(p.cashPnl)} · {fmtPercent(p.percentPnl)}
          </p>
        </div>
      </div>
    </li>
  );
}
