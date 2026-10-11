import { describe, expect, it } from 'vitest';
import { floorShares, sellPriceStart } from './sellMath';

describe('sell order math', () => {
  it('floors shares to cents without exceeding the holding', () => {
    expect(floorShares(5.009999)).toBe(5);
    expect(floorShares(5.07)).toBe(5.07);
    expect(floorShares(4.999)).toBe(4.99);
  });

  it('starts at the best bid, else the last price rounded down to the tick', () => {
    expect(sellPriceStart(0.53, 0.6, 0.01)).toBe(0.53);
    expect(sellPriceStart(null, 0.537, 0.01)).toBeCloseTo(0.53);
    expect(sellPriceStart(null, 0.0001, 0.01)).toBe(0.01);
    expect(sellPriceStart(null, 0.9999, 0.01)).toBeCloseTo(0.99);
  });
});
