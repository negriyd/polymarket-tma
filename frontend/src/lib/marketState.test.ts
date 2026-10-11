import { describe, expect, it } from 'vitest';
import type { Market } from '@/lib/api/types';
import { isTradable, marketState } from './marketState';

const base: Market = { conditionId: '0x', question: 'Q?', outcomes: ['Yes', 'No'], outcomePrices: ['0.88', '0.12'] };
const now = Date.parse('2026-10-11T00:00:00Z');

describe('marketState', () => {
  it('keeps a market tradable after its planned end date until it closes', () => {
    const s = marketState({ ...base, endDate: '2026-10-05T03:59:00Z', acceptingOrders: true }, now);
    expect(s.kind).toBe('openPastEnd');
    expect(isTradable(s)).toBe(true);
  });

  it('reads the winner of a resolved market', () => {
    expect(marketState({ ...base, closed: true, outcomePrices: ['0', '1'] }, now)).toEqual({
      kind: 'resolved',
      winnerIndex: 1,
      winner: 'No',
    });
  });

  it('is not tradable when paused or closed without a result', () => {
    expect(isTradable(marketState({ ...base, acceptingOrders: false }, now))).toBe(false);
    expect(marketState({ ...base, closed: true }, now).kind).toBe('closed');
  });
});
