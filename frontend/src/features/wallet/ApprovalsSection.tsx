import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSendTransaction } from '@privy-io/react-auth';
import { AxiosError } from 'axios';
import { api } from '@/lib/api/endpoints';
import type { ApiError, ApprovalUnsignedTx } from '@/lib/api/types';

interface Props {
  walletAddress?: string;
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

function labelFor(kind: ApprovalUnsignedTx['kind']): string {
  switch (kind) {
    case 'USDC_APPROVE':
      return 'Approve USDC for trading';
    case 'CTF_SET_APPROVAL_FOR_ALL':
      return 'Approve outcome tokens for trading';
    default:
      return kind;
  }
}

/**
 * Lists USDC + Conditional Tokens approvals required before the first order. Each unsigned tx is sent
 * via Privy {@code useSendTransaction}; on success we refetch the approval status.
 */
export function ApprovalsSection({ walletAddress }: Props) {
  const qc = useQueryClient();
  const { sendTransaction } = useSendTransaction();
  const [error, setError] = useState<string | null>(null);
  const [pendingKind, setPendingKind] = useState<string | null>(null);
  const [lastTx, setLastTx] = useState<string | null>(null);

  const approvals = useQuery({
    queryKey: ['approvals', walletAddress],
    queryFn: api.getApprovals,
    enabled: !!walletAddress,
    staleTime: 30_000,
  });

  const approve = useMutation({
    mutationFn: async (tx: ApprovalUnsignedTx) => {
      if (!walletAddress) throw new Error('Wallet not connected');
      setError(null);
      setLastTx(null);
      setPendingKind(tx.kind);
      const res = await sendTransaction(
        {
          to: tx.to,
          data: tx.data,
          value: tx.value,
          chainId: tx.chainId,
        },
        { address: walletAddress },
      );
      return res.hash;
    },
    onSuccess: (hash) => {
      setLastTx(hash);
      qc.invalidateQueries({ queryKey: ['approvals'] });
    },
    onError: (e) => setError(formatErr(e)),
    onSettled: () => setPendingKind(null),
  });

  if (!walletAddress) return null;
  const missing = approvals.data?.missing ?? [];

  return (
    <section className="space-y-2 rounded-xl bg-tg-secondary p-4">
      <header className="flex items-center justify-between gap-2">
        <div>
          <p className="text-xs uppercase tracking-wide text-tg-hint">Approvals</p>
          <p className="text-sm font-medium text-tg-text">
            {approvals.isLoading
              ? '…'
              : missing.length === 0
                ? 'All set'
                : `${missing.length} pending`}
          </p>
        </div>
        <button
          type="button"
          onClick={() => approvals.refetch()}
          disabled={approvals.isFetching}
          className="rounded-lg bg-tg-bg px-3 py-2 text-xs text-tg-hint disabled:opacity-50"
        >
          Refresh
        </button>
      </header>

      {error && <p className="text-xs text-rose-500">{error}</p>}
      {lastTx && (
        <p className="break-all text-[10px] text-emerald-500">tx: {lastTx}</p>
      )}

      {missing.length > 0 && (
        <ul className="space-y-2">
          {missing.map((tx) => (
            <li key={tx.kind} className="flex items-center justify-between gap-2 rounded-lg bg-tg-bg p-2">
              <span className="text-xs text-tg-text">{labelFor(tx.kind)}</span>
              <button
                type="button"
                disabled={approve.isPending && pendingKind === tx.kind}
                onClick={() => approve.mutate(tx)}
                className="rounded-md bg-tg-button px-3 py-1 text-xs font-semibold text-tg-buttonText disabled:opacity-50"
              >
                {approve.isPending && pendingKind === tx.kind ? 'Sending…' : 'Approve'}
              </button>
            </li>
          ))}
        </ul>
      )}

      <p className="text-[10px] leading-snug text-tg-hint">
        Required once per wallet on Polygon. USDC sets an allowance for the CTF Exchange; outcome tokens
        use ERC-1155 <code>setApprovalForAll</code>.
      </p>
    </section>
  );
}
