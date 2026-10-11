import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSignTypedData, useWallets, type SignTypedDataParams } from '@privy-io/react-auth';
import { api } from '@/lib/api/endpoints';
import type { Position } from '@/lib/api/types';
import { formatError as formatErr } from '@/lib/errors';
import { outcomeText, useT, type MessageKey } from '@/lib/i18n';
import { hapticImpact } from '@/lib/telegram/webApp';
import { placeClobOrder } from './placeOrder';
import { floorShares, sellPriceStart } from './sellMath';

const STATUS_KEYS = new Set(['matched', 'live', 'delayed', 'unmatched']);

interface Props {
  p: Position;
  walletAddress: string;
  onClose: () => void;
}

/**
 * Sells (part of) a position before the market resolves: a SELL limit order from the deposit
 * wallet, priced at the best bid by default so it fills right away. No platform fee on sells.
 */
export function SellSheet({ p, walletAddress, onClose }: Props) {
  const t = useT();
  const qc = useQueryClient();
  const { signTypedData } = useSignTypedData();
  const { wallets } = useWallets();

  const market = useQuery({ queryKey: ['market', p.conditionId], queryFn: () => api.getMarket(p.conditionId) });
  const book = useQuery({
    queryKey: ['orderbook', p.asset],
    queryFn: () => api.getOrderbook(p.conditionId, p.asset),
    refetchInterval: 10_000,
  });

  const tick = market.data?.orderPriceMinTickSize ?? 0.01;
  const tickDecimals = Math.max(0, Math.round(-Math.log10(tick)));
  const minShares = market.data?.orderMinSize ?? null;
  const held = floorShares(p.size);
  const bestBid = useMemo(() => {
    const bids = book.data?.bids ?? [];
    return bids.reduce<{ price: number; size: number } | null>(
      (best, b) => (best == null || Number(b.price) > best.price ? { price: Number(b.price), size: Number(b.size) } : best),
      null,
    );
  }, [book.data]);

  const [sharesStr, setSharesStr] = useState(String(held));
  const [priceStr, setPriceStr] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Start at the best bid (fills now); without bids, at the last price rounded down to the tick.
  const start = sellPriceStart(bestBid?.price ?? null, p.curPrice, tick);
  useEffect(() => {
    if (!priceStr && (book.isFetched || book.isError) && market.isFetched) setPriceStr(start.toFixed(tickDecimals));
  }, [priceStr, book.isFetched, book.isError, market.isFetched, start, tickDecimals]);

  const shares = floorShares(Number(sharesStr));
  const price = Number(priceStr);
  const onTick = Math.abs(price / tick - Math.round(price / tick)) < 1e-6;
  const priceOk = Number.isFinite(price) && price > 0 && price < 1 && onTick;
  const sharesOk = Number.isFinite(shares) && shares > 0 && shares <= held + 1e-9;
  const tooSmall = minShares != null && shares < minShares;
  const positionTooSmall = minShares != null && held < minShares;
  const closed = market.data != null && (market.data.closed === true || market.data.acceptingOrders === false);
  const rests = priceOk && (bestBid == null || price > bestBid.price + 1e-9);
  const proceeds = priceOk && sharesOk ? shares * price : null;
  const canSubmit = priceOk && sharesOk && !tooSmall && !closed && market.isSuccess;

  const sell = useMutation({
    mutationFn: async () => {
      setError(null);
      setSuccess(null);
      const active = wallets.find((x) => x.address.toLowerCase() === walletAddress.toLowerCase());
      return placeClobOrder({
        address: walletAddress,
        conditionId: p.conditionId,
        tokenId: p.asset,
        side: 'SELL',
        price,
        size: shares,
        sign: async (td) => (await signTypedData(td as SignTypedDataParams, { address: walletAddress })).signature,
        ensurePolygon: async () => {
          if (active && 'switchChain' in active) {
            try {
              await (active as unknown as { switchChain: (id: number) => Promise<void> }).switchChain(137);
            } catch (err) {
              console.warn('Privy wallet.switchChain(137) failed:', err);
            }
          }
        },
      });
    },
    onSuccess: (sub) => {
      const key = `trade.status.${sub.status}` as MessageKey;
      setSuccess(STATUS_KEYS.has(sub.status) ? t(key) : t('trade.statusOther', { status: sub.status }));
      hapticImpact('medium');
      void qc.invalidateQueries({ queryKey: ['positions'] });
      void qc.invalidateQueries({ queryKey: ['deposit-wallet'] });
      void qc.invalidateQueries({ queryKey: ['orderbook', p.asset] });
    },
    onError: (e) => {
      setError(formatErr(e));
      hapticImpact('light');
    },
  });

  const input = 'w-full rounded-lg bg-tg-secondary px-3 py-2 text-base tabular-nums text-tg-text outline-none';

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/50" role="dialog" aria-modal="true">
      <button type="button" className="absolute inset-0 cursor-default" aria-label={t('common.close')} onClick={onClose} />
      <div className="relative w-full max-w-lg space-y-3 rounded-t-2xl bg-tg-bg p-4 shadow-lg ring-1 ring-tg-hint/10">
        <div className="flex items-center justify-between gap-2">
          <h2 className="min-w-0 flex-1 text-lg font-semibold leading-snug text-tg-text">
            {t('sell.title', {
              outcome: outcomeText(p.outcome, t),
              question: p.title.length > 48 ? `${p.title.slice(0, 48)}…` : p.title,
            })}
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="shrink-0 rounded-lg px-2 py-1 text-sm text-tg-hint hover:bg-tg-secondary"
          >
            ✕
          </button>
        </div>

        <p className="text-xs text-tg-hint">
          {t('sell.held', { shares: held, avg: p.avgPrice.toFixed(3) })}
          {bestBid
            ? ` · ${t('sell.bestBid', { price: bestBid.price.toFixed(tickDecimals), size: Math.floor(bestBid.size) })}`
            : book.isFetched
              ? ` · ${t('sell.noBids')}`
              : ''}
        </p>

        {closed ? (
          <p className="rounded-lg bg-tg-secondary p-3 text-sm text-tg-hint">{t('sell.closed')}</p>
        ) : positionTooSmall ? (
          <p className="rounded-lg bg-tg-secondary p-3 text-sm text-tg-hint">{t('sell.positionTooSmall', { min: minShares ?? '' })}</p>
        ) : (
          <>
            <div className="grid grid-cols-2 gap-2">
              <label className="block">
                <span className="text-[11px] text-tg-hint">{t('sell.shares')}</span>
                <input
                  className={input}
                  inputMode="decimal"
                  value={sharesStr}
                  onChange={(e) => setSharesStr(e.target.value.replace(',', '.'))}
                />
              </label>
              <label className="block">
                <span className="text-[11px] text-tg-hint">{t('sell.price')}</span>
                <input
                  className={input}
                  inputMode="decimal"
                  value={priceStr}
                  onChange={(e) => setPriceStr(e.target.value.replace(',', '.'))}
                />
              </label>
            </div>
            <div className="flex gap-1">
              {[0.25, 0.5, 1].map((f) => (
                <button
                  key={f}
                  type="button"
                  onClick={() => setSharesStr(String(floorShares(held * f)))}
                  className="flex-1 rounded-md bg-tg-secondary py-1 text-xs text-tg-text"
                >
                  {f === 1 ? t('sell.all') : `${f * 100}%`}
                </button>
              ))}
            </div>

            {!sharesOk && sharesStr !== '' && <p className="text-xs text-rose-500">{t('sell.tooMany', { held })}</p>}
            {sharesOk && tooSmall && <p className="text-xs text-rose-500">{t('sell.min', { min: minShares ?? '' })}</p>}
            {priceStr !== '' && !priceOk && (
              <p className="text-xs text-rose-500">{t('sell.badPrice', { tick: tick.toFixed(tickDecimals) })}</p>
            )}
            {proceeds != null && (
              <p className="text-sm text-tg-text">
                {t('sell.proceeds', { usd: proceeds.toFixed(2) })}
                <span className="text-tg-hint">
                  {' '}
                  · {t('sell.pnl', { usd: ((price - p.avgPrice) * shares).toFixed(2) })}
                </span>
              </p>
            )}
            {rests && <p className="text-[11px] leading-snug text-amber-500">{t('sell.rests')}</p>}
            <p className="text-[10px] leading-snug text-tg-hint">{t('sell.note')}</p>
          </>
        )}

        {error && <p className="text-xs text-rose-500">{error}</p>}
        {success && <p className="text-xs text-emerald-500">{success}</p>}

        {!closed && !positionTooSmall && (
          <button
            type="button"
            disabled={!canSubmit || sell.isPending || !!success}
            onClick={() => sell.mutate()}
            className="w-full rounded-xl bg-rose-500 py-3 text-base font-semibold text-white disabled:opacity-50"
          >
            {sell.isPending ? t('trade.submitting') : t('sell.submit', { shares: sharesOk ? shares : 0 })}
          </button>
        )}
      </div>
    </div>
  );
}
