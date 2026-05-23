import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useSendTransaction } from '@privy-io/react-auth';
import { AxiosError } from 'axios';
import { api } from '@/lib/api/endpoints';
import type { ApiError, Position } from '@/lib/api/types';
import { hapticImpact } from '@/lib/telegram/webApp';

interface Props {
  p: Position;
  walletAddress?: string;
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

function formatErr(e: unknown): string {
  if (e instanceof AxiosError) {
    const d = e.response?.data as ApiError | undefined;
    if (d?.message) return d.message;
    if (e.message) return e.message;
  }
  if (e instanceof Error) return e.message;
  return 'Something went wrong';
}

/**
 * Returns "ends Jul 12", "ends in 3d" (when within a week) or "ended" if the date is past.
 * Falsy / unparseable input returns null and the caller skips the badge.
 */
function endHint(iso?: string | null): string | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  const now = Date.now();
  const diffDays = Math.round((d.getTime() - now) / (24 * 3600 * 1000));
  if (diffDays < 0) return 'ended';
  if (diffDays <= 7) return diffDays === 0 ? 'ends today' : `ends in ${diffDays}d`;
  return `ends ${d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}`;
}

/**
 * One open position. Shows the market title, outcome label colored by Yes/No, share amount and avg
 * price, current value, unrealised PnL and an inline end-date hint. When {@link Position.redeemable}
 * is true and the wallet is connected we expose a Claim button that:
 *
 *   1. Calls `POST /api/positions/redeem/prepare` to get unsigned `redeemPositions` calldata.
 *   2. Dispatches the tx via Privy `useSendTransaction` (Polygon, no value).
 *   3. Refetches positions on success — backend will drop the row once the on-chain payout lands.
 */
export function PositionRow({ p, walletAddress }: Props) {
  const yes = p.outcome?.toLowerCase() === 'yes';
  const outcomeColor = yes ? 'bg-emerald-500/15 text-emerald-500' : 'bg-rose-500/15 text-rose-500';
  const pnlColor = p.cashPnl >= 0 ? 'text-emerald-500' : 'text-rose-500';
  const ends = endHint(p.endDate);

  const qc = useQueryClient();
  const { sendTransaction } = useSendTransaction();
  const [error, setError] = useState<string | null>(null);
  const [txHash, setTxHash] = useState<string | null>(null);

  const claim = useMutation({
    mutationFn: async () => {
      if (!walletAddress) throw new Error('Wallet not connected');
      setError(null);
      setTxHash(null);
      const prep = await api.prepareRedeem({
        conditionId: p.conditionId,
        outcomeIndex: p.outcomeIndex,
      });
      const res = await sendTransaction(
        {
          to: prep.tx.to,
          data: prep.tx.data,
          value: prep.tx.value,
          chainId: prep.tx.chainId,
        },
        { address: walletAddress },
      );
      return res.hash;
    },
    onSuccess: (hash) => {
      setTxHash(hash);
      hapticImpact('medium');
      qc.invalidateQueries({ queryKey: ['positions'] });
    },
    onError: (e) => {
      setError(formatErr(e));
      hapticImpact('light');
    },
  });

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
          <div className="flex flex-wrap items-center gap-1.5">
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
            {ends && (
              <span className="rounded-md bg-tg-bg px-1.5 py-0.5 text-[10px] font-medium text-tg-hint">
                {ends}
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

      {p.redeemable && walletAddress && (
        <div className="mt-2 flex items-center justify-between gap-2 rounded-lg bg-tg-bg p-2">
          <span className="text-[11px] text-tg-hint">
            Market settled — burn {p.outcome} tokens for USDC.
          </span>
          <button
            type="button"
            disabled={claim.isPending}
            onClick={() => claim.mutate()}
            className="rounded-md bg-tg-button px-3 py-1 text-xs font-semibold text-tg-buttonText disabled:opacity-50"
          >
            {claim.isPending ? 'Claiming…' : 'Claim'}
          </button>
        </div>
      )}
      {txHash && (
        <p className="mt-1 break-all text-[10px] text-emerald-500">claim tx: {txHash}</p>
      )}
      {error && <p className="mt-1 text-[11px] text-rose-500">{error}</p>}
    </li>
  );
}
