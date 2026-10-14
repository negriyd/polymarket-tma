import type { OrderbookLevel } from '@/lib/api/types';

/** How far above the best ask an instant buy may fill. */
export const INSTANT_SLIPPAGE = 0.03;
export const INSTANT_AMOUNTS = [1, 5, 10] as const;
/** Polymarket's floor for marketable buys. */
export const INSTANT_MIN = 1;

export function bestAsk(asks: OrderbookLevel[] | undefined): number | null {
  return (asks ?? []).reduce<number | null>((best, a) => {
    const p = Number(a.price);
    return Number.isFinite(p) && Number(a.size) > 0 && (best == null || p < best) ? p : best;
  }, null);
}

/** Worst price the order accepts: best ask + slippage, up to the tick grid, kept below 1. */
export function capPrice(ask: number, tick: number): number {
  const raw = Math.ceil((ask + INSTANT_SLIPPAGE) / tick - 1e-9) * tick;
  return Math.min(1 - tick, Math.max(tick, raw));
}

/**
 * Shares `amount` buys walking the asks cheapest-first up to `cap`, and what it spends — less than
 * `amount` when the book runs dry below the cap (the rest is cancelled, not left on the book).
 */
export function walkAsks(
  asks: OrderbookLevel[] | undefined,
  amount: number,
  cap: number,
): { shares: number; spent: number } {
  let left = amount;
  let shares = 0;
  const levels = [...(asks ?? [])]
    .map((a) => ({ price: Number(a.price), size: Number(a.size) }))
    .filter((a) => a.price > 0 && a.price <= cap + 1e-9 && a.size > 0)
    .sort((x, y) => x.price - y.price);
  for (const l of levels) {
    if (left <= 1e-9) break;
    const take = Math.min(l.size, left / l.price);
    shares += take;
    left -= take * l.price;
  }
  return { shares, spent: amount - Math.max(0, left) };
}
