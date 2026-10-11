import { describe, expect, it, vi } from 'vitest';

vi.mock('@/lib/api/endpoints', () => ({ api: {} }));
vi.mock('@/lib/telegram/webApp', () => ({ tg: {} }));

const { parseStartParam } = await import('./share');

describe('parseStartParam', () => {
  it('reads the market id and the sharer pick', () => {
    expect(parseStartParam('m_559652')).toEqual({ marketId: '559652', friendPick: undefined });
    expect(parseStartParam('m_559652_y')).toEqual({ marketId: '559652', friendPick: 'yes' });
    expect(parseStartParam('m_559652_n')).toEqual({ marketId: '559652', friendPick: 'no' });
  });

  it('ignores other start params', () => {
    expect(parseStartParam(undefined)).toBeNull();
    expect(parseStartParam('ref_42')).toBeNull();
    expect(parseStartParam('m_abc')).toBeNull();
  });
});
