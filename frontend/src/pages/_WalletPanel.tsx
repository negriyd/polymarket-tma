import { useEffect, useMemo } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useWallet } from '@/features/wallet/useWallet';
import { ClobAuthSection } from '@/features/wallet/ClobAuthSection';
import { ApprovalsSection } from '@/features/wallet/ApprovalsSection';
import { PositionRow } from '@/features/trading/PositionRow';
import { Spinner } from '@/components/Spinner';
import { api } from '@/lib/api/endpoints';

export function WalletPanel() {
  const w = useWallet();

  const syncAddress = useMutation({
    mutationFn: (address: string) => api.setWalletAddress(address),
  });

  useEffect(() => {
    if (w.address) {
      syncAddress.mutate(w.address);
    }
    // intentionally not depending on syncAddress: we want a one-shot send per address.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [w.address]);

  const positions = useQuery({
    queryKey: ['positions'],
    queryFn: api.listPositions,
    enabled: !!w.address,
    refetchInterval: 30_000,
  });

  // Aggregate stats for the section header — recomputed only when positions change.
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
            <p className="text-xs uppercase tracking-wide text-tg-hint">USDC on Polygon</p>
            <p className="text-2xl font-semibold tabular-nums text-tg-text">
              {w.usdcLoading ? '…' : (w.usdcBalance?.formatted ?? '0')} USDC
            </p>
          </section>

          <ClobAuthSection walletAddress={w.address} />
          <ApprovalsSection walletAddress={w.address} />

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
