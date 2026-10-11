import { api } from '@/lib/api/endpoints';
import { tg } from '@/lib/telegram/webApp';

export type ShareResult = 'telegram' | 'link-copied' | 'native';

interface TelegramWebView {
  postEvent: (event: string, callback: unknown, data: Record<string, unknown>) => void;
}

/**
 * Shares a market card. Inside Telegram 8.0+ it opens the native chat picker with a prepared message
 * (market image, odds, "Open market" button); older clients get t.me/share with the deep link;
 * a plain browser uses the system share sheet or copies the link.
 */
export async function shareMarket(marketKey: string, side?: 'yes' | 'no'): Promise<ShareResult> {
  const share = await api.shareMarket({ marketKey, side });
  const inTelegram = !!tg.initData;

  // @twa-dev/sdk bundles an older telegram-web-app.js without shareMessage, so post the event directly.
  const webView = (window as unknown as { Telegram?: { WebView?: TelegramWebView } }).Telegram?.WebView;
  if (inTelegram && share.preparedMessageId && tg.isVersionAtLeast('8.0') && webView) {
    webView.postEvent('web_app_send_prepared_message', false, { id: share.preparedMessageId });
    return 'telegram';
  }
  if (inTelegram) {
    tg.openTelegramLink(
      `https://t.me/share/url?url=${encodeURIComponent(share.link)}&text=${encodeURIComponent(share.text)}`,
    );
    return 'telegram';
  }
  if (typeof navigator.share === 'function') {
    try {
      await navigator.share({ text: share.text, url: share.link });
      return 'native';
    } catch {
      /* cancelled or unsupported: copy instead */
    }
  }
  await navigator.clipboard.writeText(`${share.text}\n${share.link}`);
  return 'link-copied';
}

export interface MarketDeepLink {
  marketId: string;
  /** The sharer's pick, for "your friend thinks…". */
  friendPick?: 'yes' | 'no';
}

/** Parses `startapp=m_<id>[_y|_n]` from the Telegram launch parameters. */
export function parseStartParam(param?: string | null): MarketDeepLink | null {
  const m = /^m_(\d+)(?:_([yn]))?/.exec(param ?? '');
  if (!m) return null;
  return { marketId: m[1], friendPick: m[2] === 'y' ? 'yes' : m[2] === 'n' ? 'no' : undefined };
}
