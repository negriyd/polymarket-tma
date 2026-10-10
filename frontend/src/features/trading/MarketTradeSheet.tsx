import { useEffect, useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useSendTransaction, useWallets } from '@privy-io/react-auth';
import { AxiosError } from 'axios';
import { api } from '@/lib/api/endpoints';
import type { ApiError, Market } from '@/lib/api/types';
import { useWallet } from '@/features/wallet/useWallet';
import { hapticImpact } from '@/lib/telegram/webApp';

type OutcomeSide = 'yes' | 'no';

function outcomeIndexForSide(market: Market, side: OutcomeSide): number {
  const outcomes = market.outcomes ?? [];
  const idx = outcomes.findIndex((o) => o.toLowerCase() === side);
  return idx >= 0 ? idx : side === 'yes' ? 0 : 1;
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

interface Props {
  market: Market;
  outcome: OutcomeSide;
  open: boolean;
  onClose: () => void;
}

/**
 * Internal parimutuel bet sheet: prepare → USDC transfer via Privy → confirm.
 */
export function MarketTradeSheet({ market, outcome, open, onClose }: Props) {
  const qc = useQueryClient();
  const w = useWallet();
  const { sendTransaction } = useSendTransaction();
  const { wallets } = useWallets();

  const activeWallet = wallets.find(
    (wallet) => w.address && wallet.address.toLowerCase() === w.address.toLowerCase(),
  );
  const walletChainId = (() => {
    const cid = activeWallet?.chainId;
    if (!cid) return undefined;
    if (typeof cid === 'string') {
      const m = cid.match(/^eip155:(\d+)$/);
      return m ? Number(m[1]) : Number(cid);
    }
    return Number(cid);
  })();

  const outcomeIndex = outcomeIndexForSide(market, outcome);
  const outcomeLabel =
    market.outcomes?.[outcomeIndex] ?? (outcome === 'yes' ? 'YES' : 'NO');
  const cid = market.conditionId;

  const [usdcStr, setUsdcStr] = useState('5');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [paymentTxHash, setPaymentTxHash] = useState<string | null>(null);

  const betsCfg = useQuery({
    queryKey: ['bets-config'],
    queryFn: api.getBetsConfig,
    staleTime: 5 * 60_000,
  });

  const pool = useQuery({
    queryKey: ['bet-pool', cid],
    queryFn: () => api.getBetPool(cid),
    enabled: open && !!cid,
    staleTime: 15_000,
  });

  useEffect(() => {
    if (!open) return;
    setError(null);
    setSuccess(null);
    setPaymentTxHash(null);
    const min = Number(betsCfg.data?.minBetUsdc ?? 1);
    setUsdcStr(String(Math.max(min, 5)));
  }, [open, outcome, cid, betsCfg.data?.minBetUsdc]);

  const usdcNum = Number(usdcStr);
  const minBet = Number(betsCfg.data?.minBetUsdc ?? 1);
  const usdcOk = Number.isFinite(usdcNum) && usdcNum >= minBet;
  const canSubmit =
    w.ready &&
    !!cid &&
    usdcOk &&
    w.authenticated &&
    !!w.address &&
    betsCfg.data?.enabled === true;

  const poolSide = pool.data?.sides.find((s) => s.outcomeIndex === outcomeIndex);
  const impliedPct =
    poolSide && Number(poolSide.impliedProbability) > 0
      ? (Number(poolSide.impliedProbability) * 100).toFixed(1)
      : null;

  const placeBet = useMutation({
    mutationFn: async () => {
      setError(null);
      setSuccess(null);
      if (!cid || !w.address) throw new Error('Wallet not connected');
      const u = Number(usdcStr);
      if (!Number.isFinite(u) || u < minBet) throw new Error(`Minimum bet is ${minBet} USDC`);

      await api.setWalletAddress(w.address);

      if (activeWallet && 'switchChain' in activeWallet && walletChainId !== 137) {
        try {
          await (activeWallet as unknown as { switchChain: (id: number) => Promise<void> })
            .switchChain(137);
        } catch (err) {
          console.warn('Privy wallet.switchChain(137) failed:', err);
        }
      }

      const prep = await api.prepareBet({
        conditionId: cid,
        outcomeIndex,
        amountUsdc: u,
        idempotency_key: crypto.randomUUID(),
      });

      const txRes = await sendTransaction(
        {
          to: prep.paymentTx.to,
          data: prep.paymentTx.data,
          value: prep.paymentTx.value,
          chainId: prep.paymentTx.chainId,
        },
        { address: w.address },
      );
      setPaymentTxHash(txRes.hash);

      const confirmed = await api.confirmBet({
        betId: prep.betId,
        paymentTxHash: txRes.hash,
      });
      return confirmed;
    },
    onSuccess: (bet) => {
      setSuccess(`Bet confirmed · ${bet.outcomeLabel} · $${bet.amountUsdc}`);
      hapticImpact('medium');
      qc.invalidateQueries({ queryKey: ['bet-pool', cid] });
      qc.invalidateQueries({ queryKey: ['my-bets'] });
      qc.invalidateQueries({ queryKey: ['bet-balance'] });
    },
    onError: (e) => {
      setError(formatErr(e));
      hapticImpact('light');
    },
  });

  if (!open) return null;

  if (betsCfg.isLoading) {
    return (
      <SheetShell onClose={onClose}>
        <p className="text-sm text-tg-hint">Loading…</p>
      </SheetShell>
    );
  }

  if (!betsCfg.data?.enabled) {
    return (
      <SheetShell onClose={onClose}>
        <p className="rounded-lg bg-tg-secondary p-3 text-sm text-tg-hint">
          Internal betting is not enabled on this server. Operator must set{' '}
          <code className="text-tg-text">BETS_ENABLED=true</code> and a treasury address.
        </p>
      </SheetShell>
    );
  }

  return (
    <SheetShell onClose={onClose}>
      <h2 className="mb-3 text-lg font-semibold text-tg-text">
        Bet {outcomeLabel} · {market.question.slice(0, 48)}
        {market.question.length > 48 ? '…' : ''}
      </h2>

      {!w.authenticated && (
        <p className="mb-3 rounded-lg bg-tg-secondary p-3 text-sm text-tg-hint">
          Connect your wallet on the{' '}
          <Link to="/wallet" className="font-medium text-tg-button underline" onClick={onClose}>
            Wallet
          </Link>{' '}
          tab first.
        </p>
      )}

      {pool.data && (
        <div className="mb-3 rounded-xl bg-tg-secondary p-3 text-xs text-tg-hint">
          <p className="font-semibold uppercase tracking-wide text-tg-text">Parimutuel pool</p>
          <div className="mt-2 grid grid-cols-2 gap-2">
            {pool.data.sides.map((s) => (
              <div key={s.outcomeIndex} className="rounded-lg bg-tg-bg/50 px-2 py-1.5">
                <p className="font-medium text-tg-text">{s.outcomeLabel}</p>
                <p className="tabular-nums">${s.totalUsdc}</p>
                <p className="text-[10px]">
                  {(Number(s.impliedProbability) * 100).toFixed(1)}% of pool
                </p>
              </div>
            ))}
          </div>
          <p className="mt-2 text-[10px]">
            Total pool ${pool.data.totalPoolUsdc} · platform fee{' '}
            {(pool.data.platformFeeBps / 100).toFixed(2)}% taken at payout
          </p>
        </div>
      )}

      <div className="space-y-3">
        <label className="block">
          <span className="text-xs font-medium uppercase tracking-wide text-tg-hint">
            Stake (USDC)
          </span>
          <input
            type="number"
            inputMode="decimal"
            step="1"
            min={minBet}
            value={usdcStr}
            onChange={(e) => setUsdcStr(e.target.value)}
            className="mt-1 w-full rounded-xl border border-tg-hint/20 bg-tg-secondary px-3 py-2 text-tg-text"
          />
          {impliedPct && (
            <span className="mt-0.5 block text-[10px] text-tg-hint">
              Current implied share of {outcomeLabel} side: ~{impliedPct}% of pool after your bet
            </span>
          )}
          {w.usdcBalance && usdcNum > Number(w.usdcBalance.formatted) && (
            <span className="mt-0.5 block text-[10px] text-rose-500">Exceeds wallet USDC balance</span>
          )}
        </label>

        {walletChainId !== undefined && walletChainId !== 137 && (
          <p className="text-xs text-amber-500">
            Wallet is on chain {walletChainId}. We will try to switch to Polygon (137) before sending
            USDC.
          </p>
        )}

        {error && <p className="text-sm text-rose-500">{error}</p>}
        {success && <p className="text-sm text-emerald-500">{success}</p>}
        {paymentTxHash && (
          <p className="break-all text-[10px] text-emerald-500">payment tx: {paymentTxHash}</p>
        )}

        <p className="text-[10px] leading-snug text-tg-hint">
          Your stake is sent on-chain to the platform treasury. Winnings are credited to your internal
          balance after market resolution and can be withdrawn to your wallet.
        </p>

        <button
          type="button"
          disabled={!canSubmit || placeBet.isPending}
          onClick={() => placeBet.mutate()}
          className="w-full rounded-xl bg-tg-button py-3 text-base font-semibold text-tg-buttonText disabled:opacity-50"
        >
          {placeBet.isPending ? 'Sending USDC…' : `Place bet · ${outcomeLabel}`}
        </button>
      </div>
    </SheetShell>
  );
}

function SheetShell({ onClose, children }: { onClose: () => void; children: ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/50" role="dialog" aria-modal="true">
      <button type="button" className="absolute inset-0 cursor-default" aria-label="Close" onClick={onClose} />
      <div className="relative w-full max-w-lg rounded-t-2xl bg-tg-bg p-4 shadow-lg ring-1 ring-tg-hint/10">
        <div className="mb-2 flex justify-end">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg px-2 py-1 text-sm text-tg-hint hover:bg-tg-secondary"
          >
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
