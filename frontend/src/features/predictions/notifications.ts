import { api } from '@/lib/api/endpoints';
import { tg } from '@/lib/telegram/webApp';

type WriteAccess = { requestWriteAccess?: (cb: (granted: boolean) => void) => void; isVersionAtLeast?: (v: string) => boolean };

/** Whether this Telegram client can show the "allow the bot to message you" dialog (Bot API 6.9+). */
export function canAskWriteAccess(): boolean {
  const w = tg as unknown as WriteAccess;
  return !!tg.initData && typeof w.requestWriteAccess === 'function' && (w.isVersionAtLeast?.('6.9') ?? false);
}

/** Telegram's own permission dialog; on "allow" the backend starts sending result messages. */
export function askWriteAccess(): Promise<boolean> {
  const w = tg as unknown as WriteAccess;
  return new Promise((resolve) => {
    if (!canAskWriteAccess() || !w.requestWriteAccess) {
      resolve(false);
      return;
    }
    w.requestWriteAccess(async (granted) => {
      if (granted) {
        try {
          await api.setResultNotifications(true);
        } catch {
          /* the flag is also picked up on the next launch */
        }
      }
      resolve(granted);
    });
  });
}
