export const FIRST_BET_KEY = 'firstBetDone';

/** Remember that this device placed an order, so the checklist doesn't depend on open positions. */
export function markFirstBetDone() {
  try {
    localStorage.setItem(FIRST_BET_KEY, '1');
  } catch {
    /* storage blocked */
  }
}

const PREVIEW_KEY = 'firstBetChecklistPreview';

/**
 * Operator-only preview: show the checklist as a new user would see it, with this many steps
 * done (0–4). Stored on this device only; null = off.
 */
export function readChecklistPreview(): number | null {
  try {
    const raw = localStorage.getItem(PREVIEW_KEY);
    const n = raw == null ? NaN : Number(raw);
    return Number.isInteger(n) && n >= 0 && n <= 4 ? n : null;
  } catch {
    return null;
  }
}

export function writeChecklistPreview(done: number | null) {
  try {
    if (done == null) localStorage.removeItem(PREVIEW_KEY);
    else localStorage.setItem(PREVIEW_KEY, String(done));
  } catch {
    /* storage blocked */
  }
}
