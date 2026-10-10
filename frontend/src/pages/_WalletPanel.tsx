import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useWallet } from '@/features/wallet/useWallet';
import { ClobAuthSection } from '@/features/wallet/ClobAuthSection';
import { DepositWalletSection } from '@/features/wallet/DepositWalletSection';
import { PositionRow } from '@/features/trading/PositionRow';
import { Spinner } from '@/components/Spinner';
import { api } from '@/lib/api/endpoints';
import type { Bet } from '@/lib/api/types';

function statusColor(status: string): string {
  switch (status) {
    case 'WON':
    case 'CONFIRMED':
      return 'text-emerald-500';
    case 'LOST':
      return 'text-rose-500';
    case 'PENDING':
      return 'text-amber-500';
    case 'REFUNDED':
      return 'text-sky-500';
    default:
      return 'text-tg-hint';
  }
}

export function WalletPanel() {
  const qc = useQueryClient();
  const w = useWallet();
  const [withdrawStr, setWithdrawStr] = useState('');

  const syncAddress = useMutation({
    mutationFn: (address: string) => api.setWalletAddress(address),
  });

  useEffect(() => {
    if (w.address) {
      syncAddress.mutate(w.address);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [w.address]);

  const betsCfg = useQuery({
    queryKey: ['bets-config'],
    queryFn: api.getBetsConfig,
    staleTime: 5 * 60_000,
  });
  const betsEnabled = betsCfg.data?.enabled === true;

  const balance = useQuery({
    queryKey: ['bet-balance'],
    queryFn: api.getBetBalance,
    enabled: !!w.address && betsEnabled,
    refetchInterval: 30_000,
  });

  const myBets = useQuery({
    queryKey: ['my-bets'],
    queryFn: api.listMyBets,
    enabled: !!w.address && betsEnabled,
    refetchInterval: 30_000,
  });

  const positions = useQuery({
    queryKey: ['positions'],
    queryFn: api.listPositions,
    enabled: !!w.address && !betsEnabled,
    refetchInterval: 30_000,
  });

  const withdraw = useMutation({
    mutationFn: () => {
      const amount = Number(withdrawStr);
      if (!Number.isFinite(amount) || amount <= 0) {
        return Promise.reject(new Error('Enter a valid amount'));
      }
      return api.withdrawBetBalance({ amountUsdc: amount });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['bet-balance'] });
      qc.invalidateQueries({ queryKey: ['bet-withdrawals'] });
      setWithdrawStr('');
    },
  });

  const stats = useMemo(() => {
    const list = positions.data ?? [];
    if (list.length === 0) return null;
    let value = 0;
    let pnl = 0;
    for (const p of list) {
      value += p.currentValue ?? 0;
      pnl += p.cashPnl ?? 0;
    }
    return { count: list.length, value, pnl };
  }, [positions.data]);

  if (!w.ready) return <Spinner label="Initialising wallet…" />;

  return (
    <div className="max-w-full min-w-0 space-y-4 pt-2">
      <h1 className="text-2xl font-semibold">Wallet</h1>

      {!w.authenticated && (
        <button
          onClick={() => void w.login()}
          className="w-full rounded-xl bg-tg-button px-4 py-3 text-base font-semibold text-tg-buttonText"
        >
          Connect wallet
        </button>
      )}

      {w.authenticated && (
        <>
          <section className="rounded-xl bg-tg-secondary p-4">
            <p className="text-xs uppercase tracking-wide text-tg-hint">Address</p>
            <p className="break-all text-sm font-mono text-tg-text">{w.address ?? '—'}</p>
          </section>
          <section className="rounded-xl bg-tg-secondary p-4">
            <p className="text-xs uppercase tracking-wide text-tg-hint">USDC on Polygon (wallet)</p>
            <p className="text-2xl font-semibold tabular-nums text-tg-text">
              {w.usdcLoading ? '…' : (w.usdcBalance?.formatted ?? '0')} USDC
            </p>
          </section>

          {betsEnabled && (
            <>
              <section className="rounded-xl bg-tg-secondary p-4">
                <p className="text-xs uppercase tracking-wide text-tg-hint">Winnings balance</p>
                <p className="text-2xl font-semibold tabular-nums text-tg-text">
                  {balance.isLoading ? '…' : `${balance.data?.availableUsdc ?? '0'} USDC`}
                </p>
                <p className="mt-1 text-[10px] text-tg-hint">
                  Credited after winning bets resolve. Withdraw to your wallet below.
                </p>
              </section>

              <section className="rounded-xl bg-tg-secondary p-4 space-y-2">
                <h2 className="text-sm font-semibold text-tg-text">Withdraw winnings</h2>
                {!balance.data?.withdrawEnabled && (
                  <p className="text-xs text-amber-500">
                    On-chain withdraw is disabled until the operator sets BETS_TREASURY_PRIVATE_KEY.
                  </p>
                )}
                <input
                  type="number"
                  min={Number(betsCfg.data?.minWithdrawUsdc ?? 1)}
                  step="1"
                  value={withdrawStr}
                  onChange={(e) => setWithdrawStr(e.target.value)}
                  placeholder={`Min ${betsCfg.data?.minWithdrawUsdc ?? '1'} USDC`}
                  className="w-full rounded-xl border border-tg-hint/20 bg-tg-bg px-3 py-2 text-sm text-tg-text"
                />
                <button
                  type="button"
                  disabled={
                    !balance.data?.withdrawEnabled ||
                    withdraw.isPending ||
                    !withdrawStr
                  }
                  onClick={() => withdraw.mutate()}
                  className="w-full rounded-xl bg-tg-button py-2 text-sm font-semibold text-tg-buttonText disabled:opacity-50"
                >
                  {withdraw.isPending ? 'Sending…' : 'Withdraw to wallet'}
                </button>
                {withdraw.isError && (
                  <p className="text-xs text-rose-500">
                    {withdraw.error instanceof Error ? withdraw.error.message : 'Withdraw failed'}
                  </p>
                )}
                {withdraw.isSuccess && (
                  <p className="text-xs text-emerald-500">
                    Sent · tx {withdraw.data.txHash?.slice(0, 14)}…
                  </p>
                )}
              </section>

              <section className="space-y-2">
                <h2 className="text-sm font-semibold uppercase tracking-wide text-tg-hint">My bets</h2>
                {myBets.isLoading && <Spinner />}
                {myBets.data && myBets.data.length === 0 && (
                  <p className="rounded-xl bg-tg-secondary p-3 text-sm text-tg-hint">No bets yet.</p>
                )}
                <ul className="space-y-2">
                  {myBets.data?.map((b) => (
                    <BetRow key={b.id} bet={b} />
                  ))}
                </ul>
              </section>
            </>
          )}

          {!betsEnabled && (
            <>
              <ClobAuthSection walletAddress={w.address} />
              <DepositWalletSection walletAddress={w.address} />

              <section className="space-y-2">
                <header className="flex items-baseline justify-between gap-2">
                  <h2 className="text-sm font-semibold uppercase tracking-wide text-tg-hint">Positions</h2>
                  {stats && (
                    <p className="text-[11px] text-tg-hint">
                      {stats.count} · ${stats.value.toFixed(2)} ·{' '}
                      <span className={stats.pnl >= 0 ? 'text-emerald-500' : 'text-rose-500'}>
                        {stats.pnl >= 0 ? '+' : ''}${stats.pnl.toFixed(2)}
                      </span>
                    </p>
                  )}
                </header>
                {positions.isLoading && <Spinner />}
                {positions.isError && (
                  <p className="rounded-xl bg-tg-secondary p-3 text-sm text-rose-500">
                    Failed to load positions.
                  </p>
                )}
                {positions.data && positions.data.length === 0 && (
                  <p className="rounded-xl bg-tg-secondary p-3 text-sm text-tg-hint">No open positions.</p>
                )}
                <ul className="space-y-2">
                  {positions.data?.map((p) => (
                    <PositionRow key={`${p.conditionId}:${p.asset}`} p={p} walletAddress={w.address} />
                  ))}
                </ul>
              </section>
            </>
          )}

          <button
            onClick={() => void w.logout()}
            className="w-full rounded-xl bg-tg-secondary py-2 text-sm text-tg-hint"
          >
            Disconnect
          </button>
        </>
      )}
    </div>
  );
}

function BetRow({ bet }: { bet: Bet }) {
  return (
    <li className="rounded-xl bg-tg-secondary p-3 text-sm">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-medium text-tg-text">
            {bet.outcomeLabel} · ${bet.amountUsdc}
          </p>
          <p className="truncate text-[10px] text-tg-hint">{bet.conditionId}</p>
        </div>
        <span className={`shrink-0 text-xs font-semibold uppercase ${statusColor(bet.status)}`}>
          {bet.status}
        </span>
      </div>
      {bet.confirmedAt && (
        <p className="mt-1 text-[10px] text-tg-hint">
          {new Date(bet.confirmedAt).toLocaleString()}
        </p>
      )}
    </li>
  );
}
