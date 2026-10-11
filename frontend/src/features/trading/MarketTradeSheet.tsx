import { useEffect, useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useSendTransaction, useWallets } from '@privy-io/react-auth';
import { api } from '@/lib/api/endpoints';
import type { Market } from '@/lib/api/types';
import { formatError as formatErr } from '@/lib/errors';
import { Trans, useT } from '@/lib/i18n';
import { useWallet } from '@/features/wallet/useWallet';
import { hapticImpact } from '@/lib/telegram/webApp';

type OutcomeSide = 'yes' | 'no';

function outcomeIndexForSide(market: Market, side: OutcomeSide): number {
  const outcomes = market.outcomes ?? [];
  const idx = outcomes.findIndex((o) => o.toLowerCase() === side);
  return idx >= 0 ? idx : side === 'yes' ? 0 : 1;
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
  const t = useT();
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
    market.outcomes?.[outcomeIndex] ?? t(outcome === 'yes' ? 'common.yes' : 'common.no');
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
      if (!cid || !w.address) throw new Error(t('common.walletNotConnected'));
      const u = Number(usdcStr);
      if (!Number.isFinite(u) || u < minBet) throw new Error(t('bet.min', { min: minBet }));

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
      setSuccess(t('bet.confirmed', { outcome: bet.outcomeLabel, amount: bet.amountUsdc }));
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
        <p className="text-sm text-tg-hint">{t('common.loading')}</p>
      </SheetShell>
    );
  }

  if (!betsCfg.data?.enabled) {
    return (
      <SheetShell onClose={onClose}>
        <p className="rounded-lg bg-tg-secondary p-3 text-sm text-tg-hint">
          <Trans
            text={t('bet.disabled')}
            slots={{ env: <code className="text-tg-text">BETS_ENABLED=true</code> }}
          />
        </p>
      </SheetShell>
    );
  }

  return (
    <SheetShell onClose={onClose}>
      <h2 className="mb-3 text-lg font-semibold text-tg-text">
        {t('bet.title', {
          outcome: outcomeLabel,
          question: market.question.length > 48 ? `${market.question.slice(0, 48)}…` : market.question,
        })}
      </h2>

      {!w.authenticated && (
        <p className="mb-3 rounded-lg bg-tg-secondary p-3 text-sm text-tg-hint">
          <Trans
            text={t('trade.connectFirst')}
            slots={{
              wallet: (
                <Link to="/wallet" className="font-medium text-tg-button underline" onClick={onClose}>
                  {t('nav.wallet')}
                </Link>
              ),
            }}
          />
        </p>
      )}

      {pool.data && (
        <div className="mb-3 rounded-xl bg-tg-secondary p-3 text-xs text-tg-hint">
          <p className="font-semibold uppercase tracking-wide text-tg-text">{t('bet.pool')}</p>
          <div className="mt-2 grid grid-cols-2 gap-2">
            {pool.data.sides.map((s) => (
              <div key={s.outcomeIndex} className="rounded-lg bg-tg-bg/50 px-2 py-1.5">
                <p className="font-medium text-tg-text">{s.outcomeLabel}</p>
                <p className="tabular-nums">${s.totalUsdc}</p>
                <p className="text-[10px]">
                  {t('bet.ofPool', { pct: (Number(s.impliedProbability) * 100).toFixed(1) })}
                </p>
              </div>
            ))}
          </div>
          <p className="mt-2 text-[10px]">
            {t('bet.poolTotal', {
              total: pool.data.totalPoolUsdc,
              fee: (pool.data.platformFeeBps / 100).toFixed(2),
            })}
          </p>
        </div>
      )}

      <div className="space-y-3">
        <label className="block">
          <span className="text-xs font-medium uppercase tracking-wide text-tg-hint">
            {t('bet.stake')}
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
              {t('bet.impliedShare', { outcome: outcomeLabel, pct: impliedPct })}
            </span>
          )}
          {w.usdcBalance && usdcNum > Number(w.usdcBalance.formatted) && (
            <span className="mt-0.5 block text-[10px] text-rose-500">{t('bet.exceeds')}</span>
          )}
        </label>

        {walletChainId !== undefined && walletChainId !== 137 && (
          <p className="text-xs text-amber-500">
            {t('bet.wrongChain', { chain: walletChainId })}
          </p>
        )}

        {error && <p className="text-sm text-rose-500">{error}</p>}
        {success && <p className="text-sm text-emerald-500">{success}</p>}
        {paymentTxHash && (
          <p className="break-all text-[10px] text-emerald-500">{t('bet.paymentTx', { hash: paymentTxHash })}</p>
        )}

        <p className="text-[10px] leading-snug text-tg-hint">
          {t('bet.note')}
        </p>

        <button
          type="button"
          disabled={!canSubmit || placeBet.isPending}
          onClick={() => placeBet.mutate()}
          className="w-full rounded-xl bg-tg-button py-3 text-base font-semibold text-tg-buttonText disabled:opacity-50"
        >
          {placeBet.isPending ? t('bet.sending') : t('bet.place', { outcome: outcomeLabel })}
        </button>
      </div>
    </SheetShell>
  );
}

function SheetShell({ onClose, children }: { onClose: () => void; children: ReactNode }) {
  const t = useT();
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/50" role="dialog" aria-modal="true">
      <button type="button" className="absolute inset-0 cursor-default" aria-label={t('common.close')} onClick={onClose} />
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
