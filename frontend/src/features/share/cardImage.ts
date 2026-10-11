/** Public PNG of the market card; `ref` adds a partner's accuracy line. */
export function cardImageUrl(
  marketKey: string,
  lang: string,
  size: 'square' | 'story',
  ref?: string | null,
  origin: string = window.location.origin,
): string {
  const q = new URLSearchParams({ lang, size });
  if (ref) q.set('ref', ref);
  return `${origin}/api/markets/${encodeURIComponent(marketKey)}/card.png?${q}`;
}
