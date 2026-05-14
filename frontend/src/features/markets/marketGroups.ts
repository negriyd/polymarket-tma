/**
 * Polymarket home/categories — Gamma {@code tag_id} values from {@code GET /tags/slug/:slug}
 * (verified 2026-05). Labels match the official app where possible.
 *
 * - Trending: uses "Featured" carousel tag (102137); pure /markets trending has no stable tag.
 * - New: no tag — sort by {@code startDate} descending (newly listed).
 * - Mentions: Gamma tag "twitter" (128) — closest public tag; may be sparse.
 */
export type MarketGroupId =
  | 'all'
  | 'trending'
  | 'breaking'
  | 'new'
  | 'politics'
  | 'sports'
  | 'crypto'
  | 'esports'
  | 'iran'
  | 'finance'
  | 'geopolitics'
  | 'tech'
  | 'culture'
  | 'economy'
  | 'weather'
  | 'mentions'
  | 'elections';

export type MarketGroupPreset = {
  id: MarketGroupId;
  label: string;
  /** Gamma markets filter {@code tag_id}; omit when not tag-scoped. */
  tag?: string;
  /** Default sort when user switches to this group (user may change via sort chips). */
  defaultOrder: string;
  defaultAscending: boolean;
};

export const MARKET_GROUPS: MarketGroupPreset[] = [
  { id: 'all', label: 'All', defaultOrder: 'volume24hr', defaultAscending: false },
  { id: 'trending', label: 'Trending', tag: '102137', defaultOrder: 'volume24hr', defaultAscending: false },
  { id: 'breaking', label: 'Breaking', tag: '198', defaultOrder: 'volume24hr', defaultAscending: false },
  { id: 'new', label: 'New', defaultOrder: 'startDate', defaultAscending: false },
  { id: 'politics', label: 'Politics', tag: '2', defaultOrder: 'volume24hr', defaultAscending: false },
  { id: 'sports', label: 'Sports', tag: '1', defaultOrder: 'volume24hr', defaultAscending: false },
  { id: 'crypto', label: 'Crypto', tag: '21', defaultOrder: 'volume24hr', defaultAscending: false },
  { id: 'esports', label: 'Esports', tag: '64', defaultOrder: 'volume24hr', defaultAscending: false },
  { id: 'iran', label: 'Iran', tag: '78', defaultOrder: 'volume24hr', defaultAscending: false },
  { id: 'finance', label: 'Finance', tag: '120', defaultOrder: 'volume24hr', defaultAscending: false },
  { id: 'geopolitics', label: 'Geopolitics', tag: '100265', defaultOrder: 'volume24hr', defaultAscending: false },
  { id: 'tech', label: 'Tech', tag: '1401', defaultOrder: 'volume24hr', defaultAscending: false },
  { id: 'culture', label: 'Culture', tag: '596', defaultOrder: 'volume24hr', defaultAscending: false },
  { id: 'economy', label: 'Economy', tag: '100328', defaultOrder: 'volume24hr', defaultAscending: false },
  { id: 'weather', label: 'Weather', tag: '84', defaultOrder: 'volume24hr', defaultAscending: false },
  { id: 'mentions', label: 'Mentions', tag: '128', defaultOrder: 'volume24hr', defaultAscending: false },
  { id: 'elections', label: 'Elections', tag: '144', defaultOrder: 'volume24hr', defaultAscending: false },
];

export function marketGroupById(id: MarketGroupId): MarketGroupPreset {
  return MARKET_GROUPS.find((g) => g.id === id) ?? MARKET_GROUPS[0];
}
