import { AxiosError } from 'axios';
import { api } from '@/lib/api/endpoints';
import type { SubmittedOrder } from '@/lib/api/types';
import { signAndRelayBatch } from '@/features/wallet/relayerTx';

interface PlaceOrderArgs {
  address: string;
  conditionId: string;
  tokenId: string;
  side: 'BUY' | 'SELL';
  price: number;
  /** Shares, in whole cents of a share. */
  size: number;
  /** Signs EIP-712 typed data with the user's wallet (Privy) and returns the signature. */
  sign: (typedData: unknown) => Promise<string>;
  /** Puts the wallet on Polygon before signing, if needed. */
  ensurePolygon?: () => Promise<void>;
  /** The order stood but the platform fee batch failed (buys only). */
  onFeeError?: (err: unknown) => void;
}

/**
 * Polymarket CLOB v2 limit order: prepare → sign the ERC-7739 wrapper → submit, then (buys only)
 * the platform fee as a gasless pUSD transfer from the deposit wallet. Orders are POLY_1271:
 * maker = the user's deposit wallet, signer key = the Privy EOA.
 */
export async function placeClobOrder(a: PlaceOrderArgs): Promise<SubmittedOrder> {
  await api.setWalletAddress(a.address);
  const prep = await api.prepareOrder({
    conditionId: a.conditionId,
    tokenId: a.tokenId,
    side: a.side,
    price: a.price,
    size: a.size,
    signatureType: 'POLY_1271',
  });
  await a.ensurePolygon?.();
  // typedData is the ERC-7739 TypedDataSign wrapper; the backend packs the final signature.
  const signature = await a.sign(prep.typedData);

  // Submit FIRST, then charge the platform fee, so a rejected order never costs a fee.
  let submitted: SubmittedOrder;
  try {
    submitted = await api.submitOrder({ orderHash: prep.orderHash, signature, idempotency_key: crypto.randomUUID() });
  } catch (e) {
    if (e instanceof AxiosError && (e.response?.status === 401 || e.response?.status === 403)) {
      const data = e.response?.data as { code?: string } | undefined;
      if (data?.code !== 'CLOB_GEOBLOCKED') {
        // L2 credentials missing/expired: revoke so the Wallet tab prompts to reconnect.
        await api.clobAuthRevoke().catch(() => undefined);
      }
    }
    throw e;
  }

  // Best effort: the order already stands, so a failure here must not mask the success.
  if (prep.feeBatch) {
    try {
      await signAndRelayBatch(prep.feeBatch, a.sign);
    } catch (err) {
      console.error('Platform fee batch failed (order already submitted):', err);
      a.onFeeError?.(err);
    }
  }
  return submitted;
}
