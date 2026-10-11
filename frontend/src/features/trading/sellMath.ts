/** CLOB takes sizes in whole cents of a share; never round up past what the user holds. */
export function floorShares(n: number): number {
  return Number.isFinite(n) ? Math.floor(n * 100 + 1e-9) / 100 : NaN;
}

/**
 * Default SELL price: the best bid (fills right away), else the last price rounded down to the
 * tick; kept strictly inside (0, 1).
 */
export function sellPriceStart(bestBid: number | null, lastPrice: number, tick: number): number {
  const raw = bestBid ?? Math.floor(lastPrice / tick + 1e-9) * tick;
  return Math.min(1 - tick, Math.max(tick, raw));
}
