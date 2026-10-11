import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSignTypedData, useWallets, type SignTypedDataParams } from '@privy-io/react-auth';
import { api } from '@/lib/api/endpoints';
import { formatError as formatErr } from '@/lib/errors';
import { useT } from '@/lib/i18n';

interface Props {
  walletAddress?: string;
}


/**
 * Polymarket CLOB L1 key derivation. The user signs an EIP-712 payload with the wallet; the backend
 * exchanges the signature for {@code (apiKey, secret, passphrase)} and uses HMAC headers on order
 * submission. We never see those credentials in the browser.
 */
export function ClobAuthSection({ walletAddress }: Props) {
  const qc = useQueryClient();
  const t = useT();
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
      if (!walletAddress) throw new Error(t('common.walletNotConnected'));
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
      let signature: string;
      try {
        const res = await signTypedData(prep.typedData as SignTypedDataParams, {
          address: walletAddress,
        });
        signature = res.signature;
      } catch (err) {
        console.error('Privy signTypedData failed for ClobAuth:', err, prep.typedData);
        throw err;
      }
      try {
        return await api.clobAuthSubmit({
          signature,
          timestamp: prep.timestamp,
          nonce: prep.nonce,
        });
      } catch (err) {
        console.error('CLOB auth submit failed:', err);
        throw err;
      }
    },
    onSuccess: () => {
      setSuccess(t('clob.success'));
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
          <p className="text-xs uppercase tracking-wide text-tg-hint">{t('clob.title')}</p>
          <p className="text-sm font-medium text-tg-text">
            {status.isLoading ? '…' : configured ? t('clob.connected') : t('clob.notConnected')}
          </p>
        </div>
        {configured ? (
          <button
            type="button"
            disabled={disconnect.isPending}
            onClick={() => disconnect.mutate()}
            className="rounded-lg bg-tg-bg px-3 py-2 text-xs text-tg-hint disabled:opacity-50"
          >
            {t('wallet.disconnect')}
          </button>
        ) : (
          <button
            type="button"
            disabled={!walletAddress || connect.isPending}
            onClick={() => connect.mutate()}
            className="rounded-lg bg-tg-button px-3 py-2 text-xs font-semibold text-tg-buttonText disabled:opacity-50"
          >
            {connect.isPending ? t('clob.signing') : t('clob.connect')}
          </button>
        )}
      </header>
      {wrongChain && (
        <p className="text-xs text-amber-500">
          {t('clob.wrongChain', { chain: walletChainId })}
        </p>
      )}
      {error && <p className="text-xs text-rose-500">{error}</p>}
      {success && <p className="text-xs text-emerald-500">{success}</p>}
      <p className="text-[10px] leading-snug text-tg-hint">
        {t('clob.note')}
      </p>
    </section>
  );
}
