import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useSignTypedData, type SignTypedDataParams } from '@privy-io/react-auth';
import { api } from '@/lib/api/endpoints';
import type { Position } from '@/lib/api/types';
import { formatError as formatErr } from '@/lib/errors';
import { outcomeText, useLocale, useT, type Locale, type TFunction } from '@/lib/i18n';
import { hapticImpact } from '@/lib/telegram/webApp';
import { signAndRelayBatch, waitForRelayerTx } from '@/features/wallet/relayerTx';
import { SellSheet } from './SellSheet';

interface Props {
  p: Position;
  walletAddress?: string;
}

function fmtUsd(n: number): string {
  const abs = Math.abs(n);
  const fmt = abs >= 1000 ? n.toFixed(0) : n.toFixed(2);
  return `$${fmt}`;
}

/** Polymarket's data API returns `percentPnl` already in percent (-0.93 means -0.93%). */
function fmtPercent(pct: number): string {
  const sign = pct >= 0 ? '+' : '';
  return `${sign}${pct.toFixed(1)}%`;
}

function fmtShares(n: number): string {
  if (Math.abs(n) >= 100) return n.toFixed(0);
  if (Math.abs(n) >= 1) return n.toFixed(2);
  return n.toFixed(4);
}


/**
 * Returns "ends Jul 12", "ends in 3d" (when within a week) or "ended" if the date is past.
 * Falsy / unparseable input returns null and the caller skips the badge.
 */
function endHint(iso: string | null | undefined, t: TFunction, locale: Locale): string | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  const now = Date.now();
  const diffDays = Math.round((d.getTime() - now) / (24 * 3600 * 1000));
  if (diffDays < 0) return t('pos.ended');
  if (diffDays <= 7) return diffDays === 0 ? t('pos.endsToday') : t('pos.endsIn', { count: diffDays });
  return t('pos.endsOn', { date: d.toLocaleDateString(locale, { month: 'short', day: 'numeric' }) });
}

/**
 * One open position. Shows the market title, outcome label colored by Yes/No, share amount and avg
 * price, current value, unrealised PnL and an inline end-date hint. When {@link Position.redeemable}
 * is true and the wallet is connected we expose a Claim button that:
 *
 *   1. Calls `POST /api/positions/redeem/prepare-batch`: a deposit-wallet batch that redeems via the
 *      CTF collateral adapter and pays out pUSD (positions live in the deposit wallet).
 *   2. Signs it with Privy and relays it gaslessly, waiting until mined.
 *   3. Refetches positions + trading wallet balance.
 */
export function PositionRow({ p, walletAddress }: Props) {
  const t = useT();
  const locale = useLocale();
  const yes = p.outcome?.toLowerCase() === 'yes';
  const outcomeColor = yes ? 'bg-emerald-500/15 text-emerald-500' : 'bg-rose-500/15 text-rose-500';
  const pnlColor = p.cashPnl >= 0 ? 'text-emerald-500' : 'text-rose-500';
  const ends = endHint(p.endDate, t, locale);

  const qc = useQueryClient();
  const { signTypedData } = useSignTypedData();
  const [error, setError] = useState<string | null>(null);
  const [txHash, setTxHash] = useState<string | null>(null);
  const [selling, setSelling] = useState(false);
  // Still trading: not settled and the price is not pinned at 0 / 1.
  const canSell = !!walletAddress && !p.redeemable && p.size > 0 && p.curPrice > 0 && p.curPrice < 1;

  const claim = useMutation({
    mutationFn: async () => {
      if (!walletAddress) throw new Error(t('common.walletNotConnected'));
      setError(null);
      setTxHash(null);
      const batch = await api.prepareRedeemBatch({
        conditionId: p.conditionId,
        negRisk: p.negativeRisk,
      });
      const tx = await signAndRelayBatch(batch, async (td) =>
        (await signTypedData(td as SignTypedDataParams, { address: walletAddress })).signature,
      );
      if (!tx) throw new Error(t('pos.nothingToClaim'));
      const mined = await waitForRelayerTx(tx);
      return mined.transactionHash ?? tx.transactionId;
    },
    onSuccess: (hash) => {
      setTxHash(hash);
      hapticImpact('medium');
      qc.invalidateQueries({ queryKey: ['positions'] });
      qc.invalidateQueries({ queryKey: ['deposit-wallet'] });
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
              {p.outcome ? outcomeText(p.outcome, t) : '—'}
            </span>
            {p.favorite && (
              <span className="rounded-md bg-amber-500/15 px-1.5 py-0.5 text-[10px] font-medium text-amber-500">
                {t('pos.favorite')}
              </span>
            )}
            {p.redeemable && (
              <span className="rounded-md bg-tg-button/20 px-1.5 py-0.5 text-[10px] font-medium text-tg-button">
                {t('pos.redeemable')}
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
            {t('pos.sharesAt', { shares: fmtShares(p.size), avg: p.avgPrice.toFixed(3), cur: p.curPrice.toFixed(3) })}
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
            {t('pos.settled', { outcome: outcomeText(p.outcome, t) })}
          </span>
          <button
            type="button"
            disabled={claim.isPending}
            onClick={() => claim.mutate()}
            className="rounded-md bg-tg-button px-3 py-1 text-xs font-semibold text-tg-buttonText disabled:opacity-50"
          >
            {claim.isPending ? t('pos.claiming') : t('pos.claim')}
          </button>
        </div>
      )}
      {canSell && (
        <div className="mt-2 flex justify-end">
          <button
            type="button"
            onClick={() => {
              hapticImpact('light');
              setSelling(true);
            }}
            className="rounded-md bg-tg-bg px-3 py-1 text-xs font-semibold text-rose-500"
          >
            {t('sell.button')}
          </button>
        </div>
      )}
      {selling && walletAddress && <SellSheet p={p} walletAddress={walletAddress} onClose={() => setSelling(false)} />}
      {txHash && (
        <p className="mt-1 break-all text-[10px] text-emerald-500">{t('pos.claimTx', { hash: txHash })}</p>
      )}
      {error && <p className="mt-1 text-[11px] text-rose-500">{error}</p>}
    </li>
  );
}
