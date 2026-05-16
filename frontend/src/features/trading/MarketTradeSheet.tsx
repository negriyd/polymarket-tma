import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useSignTypedData, type SignTypedDataParams } from '@privy-io/react-auth';
import { AxiosError } from 'axios';
import { api } from '@/lib/api/endpoints';
import type { ApiError, Market, OrderSignatureType } from '@/lib/api/types';
import { useWallet } from '@/features/wallet/useWallet';
import { hapticImpact } from '@/lib/telegram/webApp';

type OutcomeSide = 'yes' | 'no';

function pickTokenIds(market: Market): { yesToken?: string; noToken?: string } {
  const tokens = market.clobTokenIds ?? [];
  const outcomes = market.outcomes ?? [];
  const yesIdx = outcomes.findIndex((o) => o.toLowerCase() === 'yes');
  const noIdx = outcomes.findIndex((o) => o.toLowerCase() === 'no');
  return {
    yesToken: yesIdx >= 0 ? tokens[yesIdx] : tokens[0],
    noToken: noIdx >= 0 ? tokens[noIdx] : tokens[1],
  };
}

function pickPrices(market: Market): { yes?: number; no?: number } {
  const priceAt = (idx: number): number | undefined => {
    const raw = market.outcomePrices?.[idx];
    return raw == null ? undefined : Number(raw);
  };
  const yesIdx = market.outcomes?.findIndex((o) => o.toLowerCase() === 'yes') ?? -1;
  const noIdx = market.outcomes?.findIndex((o) => o.toLowerCase() === 'no') ?? -1;
  return {
    yes: priceAt(yesIdx >= 0 ? yesIdx : 0),
    no: priceAt(noIdx >= 0 ? noIdx : 1),
  };
}

function formatErr(e: unknown): string {
  if (e instanceof AxiosError) {
    const d = e.response?.data as ApiError | undefined;
    if (d?.message) return d.message;
    if (e.message) return e.message;
  }
  if (e instanceof Error) return String(e.message);
  return 'Something went wrong';
}

interface Props {
  market: Market;
  outcome: OutcomeSide;
  open: boolean;
  onClose: () => void;
  /** Default EOA. Use {@code POLY_PROXY} (or {@code POLY_GNOSIS_SAFE}) when funds sit on a proxy. */
  signatureType?: OrderSignatureType;
  /** Required when {@link signatureType} is not EOA. The wallet signs, but maker holds the funds. */
  makerAddress?: string;
}

/**
 * Trading sheet: prepare → Privy EIP-712 sign → submit.
 * Only mount when `VITE_PRIVY_APP_ID` is set (inside `WalletProvider`).
 */
export function MarketTradeSheet({ market, outcome, open, onClose, signatureType, makerAddress }: Props) {
  const qc = useQueryClient();
  const w = useWallet();
  const { signTypedData } = useSignTypedData();

  const { yesToken, noToken } = pickTokenIds(market);
  const { yes: yesP, no: noP } = pickPrices(market);
  const tokenId = outcome === 'yes' ? yesToken : noToken;
  const refPrice = outcome === 'yes' ? yesP : noP;

  const [priceStr, setPriceStr] = useState('');
  const [usdcStr, setUsdcStr] = useState('5');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const cid = market.conditionId;

  useEffect(() => {
    if (!open) return;
    setError(null);
    setSuccess(null);
    if (refPrice != null) {
      setPriceStr(String(Math.min(0.99, Math.max(0.01, Number(refPrice)))));
    } else {
      setPriceStr('0.5');
    }
  }, [open, outcome, refPrice, market.conditionId]);

  const priceNum = Number(priceStr);
  const usdcNum = Number(usdcStr);
  const priceOk = Number.isFinite(priceNum) && priceNum > 0 && priceNum < 1;
  const usdcOk = Number.isFinite(usdcNum) && usdcNum > 0;
  const shares = priceOk && usdcOk ? usdcNum / priceNum : null;
  const canSubmit =
    w.ready &&
    !!tokenId &&
    !!cid &&
    priceOk &&
    usdcOk &&
    shares != null &&
    shares > 0 &&
    w.authenticated &&
    !!w.address;

  const trade = useMutation({
    mutationFn: async () => {
      setError(null);
      setSuccess(null);
      const p = Number(priceStr);
      const u = Number(usdcStr);
      const sh = Number.isFinite(p) && Number.isFinite(u) && p > 0 && p < 1 && u > 0 ? u / p : null;
      if (!cid || !tokenId) throw new Error('Market is missing token id');
      if (!w.address) throw new Error('Wallet not connected');
      if (sh == null || sh <= 0) throw new Error('Invalid amount or price');

      await api.setWalletAddress(w.address);

      const sizeRounded = Math.round(sh * 1_000_000) / 1_000_000;
      const prep = await api.prepareOrder({
        conditionId: cid,
        tokenId,
        side: 'BUY',
        price: p,
        size: sizeRounded,
        signatureType,
        makerAddress,
      });

      const { signature } = await signTypedData(prep.typedData as SignTypedDataParams, {
        address: w.address,
      });

      try {
        return await api.submitOrder({
          orderHash: prep.orderHash,
          signature,
          idempotency_key: crypto.randomUUID(),
        });
      } catch (e) {
        if (
          e instanceof AxiosError &&
          (e.response?.status === 401 || e.response?.status === 403)
        ) {
          // CLOB likely rejected the request because the L2 credentials expired/are missing.
          // Revoke locally so the wallet page prompts to re-connect Polymarket trading.
          try {
            await api.clobAuthRevoke();
          } catch {
            /* best effort */
          }
        }
        throw e;
      }
    },
    onSuccess: (sub) => {
      setSuccess(`Order ${sub.status}${sub.orderId ? ` · ${sub.orderId.slice(0, 10)}…` : ''}`);
      hapticImpact('medium');
      qc.invalidateQueries({ queryKey: ['positions'] });
      qc.invalidateQueries({ queryKey: ['market', cid ?? ''] });
    },
    onError: (e) => {
      setError(formatErr(e));
      hapticImpact('light');
    },
  });

  const displayPriceHint =
    refPrice != null ? `Market ~${Math.round(refPrice * 100)}¢` : 'Enter limit price (0–1)';

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/50" role="dialog" aria-modal="true">
      <button type="button" className="absolute inset-0 cursor-default" aria-label="Close" onClick={onClose} />
      <div className="relative w-full max-w-lg rounded-t-2xl bg-tg-bg p-4 shadow-lg ring-1 ring-tg-hint/10">
        <div className="mb-3 flex items-center justify-between gap-2">
          <h2 className="min-w-0 flex-1 text-lg font-semibold leading-snug text-tg-text">
            Buy {outcome.toUpperCase()} · {market.question.slice(0, 48)}
            {market.question.length > 48 ? '…' : ''}
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="shrink-0 rounded-lg px-2 py-1 text-sm text-tg-hint hover:bg-tg-secondary"
          >
            ✕
          </button>
        </div>

        {!w.authenticated && (
          <p className="mb-3 rounded-lg bg-tg-secondary p-3 text-sm text-tg-hint">
            Connect your wallet on the{' '}
            <Link to="/wallet" className="font-medium text-tg-button underline" onClick={onClose}>
              Wallet
            </Link>{' '}
            tab first.
          </p>
        )}

        {!tokenId && (
          <p className="mb-3 text-sm text-rose-500">This market has no CLOB token id; trading is unavailable.</p>
        )}

        <div className="space-y-3">
          <label className="block">
            <span className="text-xs font-medium uppercase tracking-wide text-tg-hint">Limit price (USDC per share)</span>
            <input
              type="number"
              inputMode="decimal"
              step="0.001"
              min={0.01}
              max={0.99}
              value={priceStr}
              onChange={(e) => setPriceStr(e.target.value)}
              className="mt-1 w-full rounded-xl border border-tg-hint/20 bg-tg-secondary px-3 py-2 text-tg-text"
            />
            <span className="mt-0.5 block text-[10px] text-tg-hint">{displayPriceHint}</span>
          </label>

          <label className="block">
            <span className="text-xs font-medium uppercase tracking-wide text-tg-hint">Spend (USDC)</span>
            <input
              type="number"
              inputMode="decimal"
              step="1"
              min={1}
              value={usdcStr}
              onChange={(e) => setUsdcStr(e.target.value)}
              className="mt-1 w-full rounded-xl border border-tg-hint/20 bg-tg-secondary px-3 py-2 text-tg-text"
            />
            {shares != null && priceOk && (
              <p className="mt-1 text-xs text-tg-hint">
                ≈ {shares.toFixed(4)} shares
                {w.usdcBalance && usdcNum > Number(w.usdcBalance.formatted) ? ' · exceeds USDC balance' : ''}
              </p>
            )}
          </label>

          {error && <p className="text-sm text-rose-500">{error}</p>}
          {success && <p className="text-sm text-emerald-500">{success}</p>}

          <p className="text-[10px] leading-snug text-tg-hint">
            Requires USDC on Polygon and contract approvals (see docs/trading.md). CLOB may reject orders until API
            credentials and approvals are complete.
          </p>

          <button
            type="button"
            disabled={!canSubmit || trade.isPending}
            onClick={() => trade.mutate()}
            className="w-full rounded-xl bg-tg-button py-3 text-base font-semibold text-tg-buttonText disabled:opacity-50"
          >
            {trade.isPending ? 'Signing / submitting…' : 'Review & place order'}
          </button>
        </div>
      </div>
    </div>
  );
}
