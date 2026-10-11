import { api } from '@/lib/api/endpoints';
import type { PreparedBatch, RelayerTx } from '@/lib/api/types';
import { t } from '@/lib/i18n';

const SUCCESS = new Set(['STATE_MINED', 'STATE_CONFIRMED']);
const FAILURE = new Set(['STATE_FAILED', 'STATE_INVALID']);

/**
 * Polls a gasless relayer transaction until it is mined. Mirrors `wait()` in
 * `@polymarket/builder-relayer-client` (2 s interval, ~3 min cap).
 */
export async function waitForRelayerTx(tx: RelayerTx, maxPolls = 90): Promise<RelayerTx> {
  let current = tx;
  for (let i = 0; i < maxPolls; i++) {
    if (SUCCESS.has(current.state)) return current;
    if (FAILURE.has(current.state)) {
      throw new Error(t('relayer.failed', { state: current.state.replace('STATE_', '').toLowerCase() }));
    }
    await new Promise((r) => setTimeout(r, 2_000));
    try {
      current = await api.getRelayerTx(tx.transactionId);
    } catch {
      // The relayer may not index a fresh id immediately; keep polling.
    }
  }
  throw new Error(t('relayer.timeout'));
}

/** Privy `signTypedData` bound to the owner address. */
export type SignTypedData = (typedData: Record<string, unknown>) => Promise<string>;

/**
 * Signs a server-prepared deposit-wallet batch with the owner's key and hands it to the relayer.
 * Returns null when the batch is empty (nothing to do).
 */
export async function signAndRelayBatch(batch: PreparedBatch, sign: SignTypedData): Promise<RelayerTx | null> {
  if (batch.callCount === 0 || !batch.batchHash || !batch.typedData) return null;
  const signature = await sign(batch.typedData);
  return api.submitDepositWalletBatch({ batchHash: batch.batchHash, signature });
}
