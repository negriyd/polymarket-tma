import type { Query } from '@tanstack/react-query';
import { formatUnits } from 'viem';
import type { BridgeDeposit, FundingOption } from '@/lib/api/types';

export const IN_FLIGHT = new Set(['DEPOSIT_DETECTED', 'PROCESSING', 'ORIGIN_TX_CONFIRMED', 'SUBMITTED']);

/** Poll fast while a deposit is moving through the bridge. */
export const depositsRefetch = (q: Query<BridgeDeposit[]>) =>
  (q.state.data ?? []).some((d) => IN_FLIGHT.has(d.status)) ? 10_000 : 30_000;

export function formatAmount(d: BridgeDeposit, options: FundingOption[]): string {
  const opt = options.find(
    (o) => o.chainId === d.fromChainId && o.tokenAddress.toLowerCase() === d.fromTokenAddress.toLowerCase(),
  );
  if (!opt || opt.decimals == null) return '';
  try {
    return `${Number(formatUnits(BigInt(d.fromAmountBaseUnit), opt.decimals)).toFixed(2)} ${opt.symbol}`;
  } catch {
    return '';
  }
}
