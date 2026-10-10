import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSignTypedData, type SignTypedDataParams } from '@privy-io/react-auth';
import { AxiosError } from 'axios';
import { formatUnits } from 'viem';
import { api } from '@/lib/api/endpoints';
import type { ApiError } from '@/lib/api/types';
import { signAndRelayBatch, waitForRelayerTx } from './relayerTx';
import { FundingPanel } from './FundingPanel';
import { WithdrawPanel } from './WithdrawPanel';

interface Props {
  walletAddress?: string;
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
 * Polymarket CLOB v2 trades through a per-user deposit wallet (smart contract owned by the Privy
 * EOA). Onboarding is gasless via Polymarket's relayer: deploy the wallet, then sign one batch of
 * pUSD / outcome-token approvals. Funds (pUSD) are held by the deposit wallet, not the EOA.
 */
export function DepositWalletSection({ walletAddress }: Props) {
  const qc = useQueryClient();
  const { signTypedData } = useSignTypedData();
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [panel, setPanel] = useState<'topup' | 'withdraw' | null>(null);

  const status = useQuery({
    queryKey: ['deposit-wallet', walletAddress],
    queryFn: api.getDepositWallet,
    enabled: !!walletAddress,
    refetchInterval: 30_000,
  });

  const refresh = () => qc.invalidateQueries({ queryKey: ['deposit-wallet'] });

  const deploy = useMutation({
    mutationFn: async () => {
      setError(null);
      await waitForRelayerTx(await api.deployDepositWallet());
    },
    onSuccess: refresh,
    onError: (e) => setError(formatErr(e)),
  });

  const approve = useMutation({
    mutationFn: async () => {
      if (!walletAddress) throw new Error('Wallet not connected');
      setError(null);
      const tx = await signAndRelayBatch(await api.prepareDepositWalletApprovals(), async (td) =>
        (await signTypedData(td as SignTypedDataParams, { address: walletAddress })).signature,
      );
      if (tx) await waitForRelayerTx(tx);
    },
    onSuccess: refresh,
    onError: (e) => setError(formatErr(e)),
  });

  if (!walletAddress) return null;
  const s = status.data;
  const missing = s?.approvals.filter((a) => a.approved !== true).length ?? 0;
  const balance = s?.collateralBalance != null ? formatUnits(BigInt(s.collateralBalance), 6) : null;
  const busy = deploy.isPending || approve.isPending;

  const copyAddress = async () => {
    if (!s) return;
    try {
      await navigator.clipboard.writeText(s.address);
      setCopied(true);
      setTimeout(() => setCopied(false), 1_500);
    } catch {
      /* clipboard may be unavailable inside some Telegram clients */
    }
  };

  return (
    <section className="space-y-2 rounded-xl bg-tg-secondary p-4">
      <header className="flex items-center justify-between gap-2">
        <div>
          <p className="text-xs uppercase tracking-wide text-tg-hint">Trading wallet</p>
          <p className="text-sm font-medium text-tg-text">
            {status.isLoading
              ? '…'
              : !s
                ? 'Unavailable'
                : s.readyToTrade
                  ? 'Ready to trade'
                  : !s.deployed
                    ? 'Not activated'
                    : `${missing} approval${missing === 1 ? '' : 's'} pending`}
          </p>
        </div>
        {s && !s.deployed && (
          <button
            type="button"
            disabled={busy}
            onClick={() => deploy.mutate()}
            className="rounded-lg bg-tg-button px-3 py-2 text-xs font-semibold text-tg-buttonText disabled:opacity-50"
          >
            {deploy.isPending ? 'Activating…' : 'Activate'}
          </button>
        )}
        {s && s.deployed && missing > 0 && (
          <button
            type="button"
            disabled={busy}
            onClick={() => approve.mutate()}
            className="rounded-lg bg-tg-button px-3 py-2 text-xs font-semibold text-tg-buttonText disabled:opacity-50"
          >
            {approve.isPending ? 'Approving…' : 'Approve'}
          </button>
        )}
      </header>

      {s && (
        <>
          <div>
            <p className="text-[10px] uppercase tracking-wide text-tg-hint">Balance</p>
            <p className="text-2xl font-semibold tabular-nums text-tg-text">
              {balance ?? '—'} <span className="text-base font-normal text-tg-hint">pUSD</span>
            </p>
          </div>
          <button
            type="button"
            onClick={() => void copyAddress()}
            className="w-full rounded-lg bg-tg-bg p-2 text-left"
          >
            <span className="block text-[10px] uppercase tracking-wide text-tg-hint">
              Wallet address (Polygon) · {copied ? 'copied' : 'tap to copy'}
            </span>
            <span className="block break-all font-mono text-xs text-tg-text">{s.address}</span>
          </button>
          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={() => setPanel((v) => (v === 'topup' ? null : 'topup'))}
              className="rounded-lg bg-tg-button py-2 text-sm font-semibold text-tg-buttonText"
            >
              {panel === 'topup' ? 'Hide' : 'Top up'}
            </button>
            <button
              type="button"
              disabled={!s.deployed}
              onClick={() => setPanel((v) => (v === 'withdraw' ? null : 'withdraw'))}
              className="rounded-lg bg-tg-bg py-2 text-sm font-semibold text-tg-text disabled:opacity-50"
            >
              {panel === 'withdraw' ? 'Hide' : 'Withdraw'}
            </button>
          </div>
          {panel === 'topup' && <FundingPanel />}
          {panel === 'withdraw' && <WithdrawPanel walletAddress={walletAddress} />}
        </>
      )}

      {error && <p className="text-xs text-rose-500">{error}</p>}
      {status.isError && <p className="text-xs text-rose-500">{formatErr(status.error)}</p>}

      <p className="text-[10px] leading-snug text-tg-hint">
        Polymarket trades from a smart wallet controlled by your key. Activation and approvals are
        gasless. Orders are paid in pUSD held by this wallet — don&apos;t send tokens to the wallet
        address directly; use Top up, which converts them to pUSD.
      </p>
    </section>
  );
}
