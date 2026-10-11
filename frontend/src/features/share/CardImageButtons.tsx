import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api/endpoints';
import { useLocale, useT } from '@/lib/i18n';
import { hapticImpact, tg } from '@/lib/telegram/webApp';
import { cardImageUrl } from './cardImage';

interface TelegramWebView {
  postEvent: (event: string, callback: unknown, data: Record<string, unknown>) => void;
}

const webView = () => (window as unknown as { Telegram?: { WebView?: TelegramWebView } }).Telegram?.WebView;

/**
 * Picture of the market (odds, chart, link) for posts outside Telegram and for Telegram stories.
 * Download uses Telegram's file download (Bot API 8.0), stories use shareToStory (7.8).
 */
export function CardImageButtons({ marketKey }: { marketKey: string }) {
  const t = useT();
  const locale = useLocale();
  const me = useQuery({ queryKey: ['referrals-me'], queryFn: api.getMyReferrals, staleTime: 60_000 });
  const ref = me.data?.partner ? me.data.code : null;
  const inTelegram = !!tg.initData;
  const wv = webView();

  const download = () => {
    hapticImpact('light');
    const url = cardImageUrl(marketKey, locale, 'square', ref);
    if (inTelegram && wv && tg.isVersionAtLeast('8.0')) {
      wv.postEvent('web_app_request_file_download', false, { url, file_name: `market-${marketKey}.png` });
    } else if (inTelegram) {
      tg.openLink(url);
    } else {
      window.open(url, '_blank');
    }
  };

  const story = () => {
    hapticImpact('light');
    const url = cardImageUrl(marketKey, locale, 'story', ref);
    const link =
      me.data && /^\d+$/.test(marketKey)
        ? `${me.data.link.split('?')[0]}?startapp=m_${marketKey}_r${me.data.code}`
        : undefined;
    wv?.postEvent('web_app_share_to_story', false, {
      media_url: url,
      text: t('card.storyText'),
      ...(link && tg.initDataUnsafe?.user?.is_premium ? { widget_link: { url: link, name: t('card.storyWidget') } } : {}),
    });
  };

  return (
    <div className="flex gap-2">
      <button
        type="button"
        onClick={download}
        className="flex-1 rounded-xl bg-tg-secondary py-2 text-xs font-semibold text-tg-text"
      >
        🖼 {t('card.download')}
      </button>
      {inTelegram && wv && tg.isVersionAtLeast('7.8') && (
        <button
          type="button"
          onClick={story}
          className="flex-1 rounded-xl bg-tg-secondary py-2 text-xs font-semibold text-tg-text"
        >
          📱 {t('card.story')}
        </button>
      )}
    </div>
  );
}
