import type { Market } from '@/lib/api/types';

/**
 * Where a market is in its life. Polymarket's endDate is only the planned date: a market keeps
 * trading after it until the official result (e.g. an election run-off), and closes on resolution.
 */
export type MarketState =
  | { kind: 'open' }
  | { kind: 'openPastEnd' }
  | { kind: 'paused' }
  | { kind: 'closed' }
  | { kind: 'resolved'; winnerIndex: number; winner: string };

export function marketState(m: Market, now = Date.now()): MarketState {
  if (m.closed) {
    const prices = (m.outcomePrices ?? []).map(Number);
    const winnerIndex = prices.findIndex((p) => p >= 0.99);
    if (winnerIndex >= 0) {
      const winner = m.outcomes?.[winnerIndex] ?? (winnerIndex === 0 ? 'Yes' : 'No');
      return { kind: 'resolved', winnerIndex, winner };
    }
    return { kind: 'closed' };
  }
  if (m.acceptingOrders === false) return { kind: 'paused' };
  if (m.endDate && new Date(m.endDate).getTime() < now) return { kind: 'openPastEnd' };
  return { kind: 'open' };
}

export const isTradable = (s: MarketState) => s.kind === 'open' || s.kind === 'openPastEnd';
