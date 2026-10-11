export const FIRST_BET_KEY = 'firstBetDone';

/** Remember that this device placed an order, so the checklist doesn't depend on open positions. */
export function markFirstBetDone() {
  try {
    localStorage.setItem(FIRST_BET_KEY, '1');
  } catch {
    /* storage blocked */
  }
}
