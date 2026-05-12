import { useEffect } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useWallet } from '@/features/wallet/useWallet';
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

  if (!w.ready) return <Spinner label="Initialising wallet…" />;

  return (
    <div className="space-y-4 pt-2">
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

          <section className="space-y-2">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-tg-hint">Positions</h2>
            {positions.isLoading && <Spinner />}
            {positions.data && positions.data.length === 0 && (
              <p className="rounded-xl bg-tg-secondary p-3 text-sm text-tg-hint">No open positions.</p>
            )}
            <ul className="space-y-2">
              {positions.data?.map((p) => (
                <li
                  key={p.tokenId}
                  className="flex items-center justify-between rounded-xl bg-tg-secondary p-3 text-sm"
                >
                  <div className="min-w-0">
                    <p className="truncate font-medium text-tg-text">{p.outcome}</p>
                    <p className="text-xs text-tg-hint">{p.size.toFixed(2)} @ ${p.avgPrice.toFixed(2)}</p>
                  </div>
                  <div className={p.pnl >= 0 ? 'text-emerald-500' : 'text-rose-500'}>
                    <p className="text-right tabular-nums">${p.currentValue.toFixed(2)}</p>
                    <p className="text-right text-xs tabular-nums">{p.pnl >= 0 ? '+' : ''}{p.pnl.toFixed(2)}</p>
                  </div>
                </li>
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
