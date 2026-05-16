import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSignTypedData, useWallets, type SignTypedDataParams } from '@privy-io/react-auth';
import { AxiosError } from 'axios';
import { api } from '@/lib/api/endpoints';
import type { ApiError } from '@/lib/api/types';

interface Props {
  walletAddress?: string;
}

interface PrivyErrorShape {
  code?: string;
  type?: string;
  cause?: unknown;
  details?: unknown;
}

function formatErr(e: unknown): string {
  if (e instanceof AxiosError) {
    const d = e.response?.data as ApiError | undefined;
    if (d?.message) return d.message;
    if (e.message) return e.message;
  }
  if (e instanceof Error) {
    const extra = e as Error & PrivyErrorShape;
    const code = extra.code ?? extra.type;
    if (code) return `${code}: ${e.message}`;
    return e.message;
  }
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
  const { wallets } = useWallets();
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const activeWallet = wallets.find(
    (w) => walletAddress && w.address.toLowerCase() === walletAddress.toLowerCase(),
  );
  /**
   * Privy embedded wallets sometimes default to a non-Polygon chain regardless of
   * `defaultChain` in the provider config; signing then fails with a generic error.
   * We surface the mismatch so the user can switch in their wallet UI.
   */
  const walletChainId = (() => {
    const cid = activeWallet?.chainId;
    if (!cid) return undefined;
    if (typeof cid === 'string') {
      const m = cid.match(/^eip155:(\d+)$/);
      return m ? Number(m[1]) : Number(cid);
    }
    return Number(cid);
  })();
  const wrongChain = walletChainId !== undefined && walletChainId !== 137;

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
      if (activeWallet && 'switchChain' in activeWallet && walletChainId !== 137) {
        try {
          await (activeWallet as unknown as { switchChain: (id: number) => Promise<void> })
            .switchChain(137);
        } catch (err) {
          console.warn('Privy wallet.switchChain(137) failed:', err);
        }
      }
      try {
        const { signature } = await signTypedData(prep.typedData as SignTypedDataParams, {
          address: walletAddress,
        });
        return await api.clobAuthSubmit({
          signature,
          timestamp: prep.timestamp,
          nonce: prep.nonce,
        });
      } catch (err) {
        console.error('Privy signTypedData failed for ClobAuth:', err, prep.typedData);
        throw err;
      }
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
      {wrongChain && (
        <p className="text-xs text-amber-500">
          Wallet is on chain {walletChainId}. Polymarket uses Polygon (137); we will try to switch on
          connect, but if your wallet UI shows a different network please change it manually.
        </p>
      )}
      {error && <p className="text-xs text-rose-500">{error}</p>}
      {success && <p className="text-xs text-emerald-500">{success}</p>}
      <p className="text-[10px] leading-snug text-tg-hint">
        Sign once to derive CLOB API credentials; the server stores them encrypted and signs every order
        request on your behalf. Disconnect to revoke.
      </p>
    </section>
  );
}
