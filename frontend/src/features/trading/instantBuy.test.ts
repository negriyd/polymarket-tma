import { describe, expect, it } from 'vitest';
import { bestAsk, capPrice, walkAsks } from './instantBuy';

describe('instant buy', () => {
  const asks = [
    { price: 0.55, size: 4 },
    { price: 0.53, size: 2 },
    { price: 0.6, size: 100 },
  ];

  it('finds the cheapest ask with size', () => {
    expect(bestAsk(asks)).toBe(0.53);
    expect(bestAsk([{ price: 0.4, size: 0 }])).toBeNull();
    expect(bestAsk(undefined)).toBeNull();
  });

  it('caps three cents above the best ask on the tick grid, below 1', () => {
    expect(capPrice(0.53, 0.01)).toBeCloseTo(0.56);
    expect(capPrice(0.531, 0.001)).toBeCloseTo(0.561);
    expect(capPrice(0.98, 0.01)).toBeCloseTo(0.99);
  });

  it('walks the book cheapest first and stops at the cap', () => {
    const r = walkAsks(asks, 5, 0.56);
    // 2 @ 0.53 = 1.06, then 3.94 / 0.55 = 7.16 > 4 available -> 4 @ 0.55 = 2.20; 0.60 is above the cap.
    expect(r.shares).toBeCloseTo(6);
    expect(r.spent).toBeCloseTo(3.26);
    expect(walkAsks(asks, 1, 0.56).shares).toBeCloseTo(1 / 0.53);
  });
});
