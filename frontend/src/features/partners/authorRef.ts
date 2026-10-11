const KEY = 'author-ref';

/** Invite code (`r<code>` token) of our `startapp` value, if any. */
export function refCodeOf(startParam?: string | null): string | null {
  for (const tok of (startParam ?? '').split('_')) {
    if (/^r[a-z0-9]{4,16}$/.test(tok)) return tok.slice(1);
  }
  return null;
}

/**
 * Remembers whose link the app was last opened from, so the author badge keeps showing on later
 * launches without a link. Without one, the backend falls back to whoever invited the user.
 */
export function rememberAuthorRef(code: string | null): void {
  if (!code) return;
  try {
    localStorage.setItem(KEY, code);
  } catch {
    /* storage unavailable */
  }
}

export function authorRef(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}
