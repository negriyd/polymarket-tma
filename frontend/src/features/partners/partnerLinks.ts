export const usd = (v: string) =>
  `$${Number(v).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

/** Link tag as the backend accepts it: `s` + [a-z0-9-]{1,30}. */
export function cleanTag(raw: string): string {
  return raw
    .toLowerCase()
    .replace(/[^a-z0-9-]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 30);
}

export function taggedLink(link: string, tag: string): string {
  const clean = cleanTag(tag);
  return clean ? `${link}_s${clean}` : link;
}
