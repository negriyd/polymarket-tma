import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSignTypedData, type SignTypedDataParams } from '@privy-io/react-auth';
import { AxiosError } from 'axios';
import { api } from '@/lib/api/endpoints';
import type { ApiError } from '@/lib/api/types';

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

/**
 * Polymarket CLOB L1 key derivation. The user signs an EIP-712 payload with the wallet; the backend
 * exchanges the signature for {@code (apiKey, secret, passphrase)} and uses HMAC headers on order
 * submission. We never see those credentials in the browser.
 */
export function ClobAuthSection({ walletAddress }: Props) {
  const qc = useQueryClient();
  const { signTypedData } = useSignTypedData();
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const status = useQuery({
    queryKey: ['clob-auth', walletAddress],
    queryFn: api.clobAuthStatus,
    enabled: !!walletAddress,
    staleTime: 60_000,
  });

  const connect = useMutation({
    mutationFn: async () => {
      if (!walletAddress) throw new Error('Wallet not connected');
      setError(null);
      setSuccess(null);
      const prep = await api.clobAuthPrepare();
      const { signature } = await signTypedData(prep.typedData as SignTypedDataParams, {
        address: walletAddress,
      });
      return api.clobAuthSubmit({
        signature,
        timestamp: prep.timestamp,
        nonce: prep.nonce,
      });
    },
    onSuccess: () => {
      setSuccess('Connected to Polymarket trading');
      qc.invalidateQueries({ queryKey: ['clob-auth'] });
    },
    onError: (e) => setError(formatErr(e)),
  });

  const disconnect = useMutation({
    mutationFn: () => api.clobAuthRevoke(),
    onSuccess: () => {
      setSuccess(null);
      setError(null);
      qc.invalidateQueries({ queryKey: ['clob-auth'] });
    },
    onError: (e) => setError(formatErr(e)),
  });

  const configured = status.data?.configured === true;

  return (
    <section className="space-y-2 rounded-xl bg-tg-secondary p-4">
      <header className="flex items-center justify-between gap-2">
        <div>
          <p className="text-xs uppercase tracking-wide text-tg-hint">Polymarket trading</p>
          <p className="text-sm font-medium text-tg-text">
            {status.isLoading ? '…' : configured ? 'Connected' : 'Not connected'}
          </p>
        </div>
        {configured ? (
          <button
            type="button"
            disabled={disconnect.isPending}
            onClick={() => disconnect.mutate()}
            className="rounded-lg bg-tg-bg px-3 py-2 text-xs text-tg-hint disabled:opacity-50"
          >
            Disconnect
          </button>
        ) : (
          <button
            type="button"
            disabled={!walletAddress || connect.isPending}
            onClick={() => connect.mutate()}
            className="rounded-lg bg-tg-button px-3 py-2 text-xs font-semibold text-tg-buttonText disabled:opacity-50"
          >
            {connect.isPending ? 'Signing…' : 'Connect'}
          </button>
        )}
      </header>
      {error && <p className="text-xs text-rose-500">{error}</p>}
      {success && <p className="text-xs text-emerald-500">{success}</p>}
      <p className="text-[10px] leading-snug text-tg-hint">
        Sign once to derive CLOB API credentials; the server stores them encrypted and signs every order
        request on your behalf. Disconnect to revoke.
      </p>
    </section>
  );
}
