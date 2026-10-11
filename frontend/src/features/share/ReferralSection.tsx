import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api/endpoints';
import { useT } from '@/lib/i18n';
import { hapticImpact, tg } from '@/lib/telegram/webApp';

/** Invite link and what it has brought: friends who signed up and the fee share paid out. */
export function ReferralSection() {
  const t = useT();
  const [copied, setCopied] = useState(false);
  const me = useQuery({ queryKey: ['referrals-me'], queryFn: api.getMyReferrals, staleTime: 60_000 });

  if (!me.data) return null;
  const r = me.data;

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(r.link);
      setCopied(true);
      setTimeout(() => setCopied(false), 1_500);
    } catch {
      /* clipboard may be unavailable inside some Telegram clients */
    }
  };

  const share = () => {
    hapticImpact('light');
    const url = `https://t.me/share/url?url=${encodeURIComponent(r.link)}&text=${encodeURIComponent(t('ref.shareText'))}`;
    if (tg.initData) tg.openTelegramLink(url);
    else window.open(url, '_blank');
  };

  return (
    <section className="space-y-2 rounded-xl bg-tg-secondary p-4">
      <div>
        <p className="text-xs uppercase tracking-wide text-tg-hint">{t('ref.title')}</p>
        <p className="mt-0.5 text-[11px] leading-snug text-tg-hint">
          {t('ref.terms', { pct: r.sharePercent, months: r.months })}
        </p>
      </div>
      <div className="grid grid-cols-2 gap-2">
        <div className="rounded-lg bg-tg-bg px-3 py-2">
          <p className="text-[10px] uppercase tracking-wide text-tg-hint">{t('ref.invited')}</p>
          <p className="text-lg font-semibold tabular-nums text-tg-text">{r.invited}</p>
        </div>
        <div className="rounded-lg bg-tg-bg px-3 py-2">
          <p className="text-[10px] uppercase tracking-wide text-tg-hint">{t('ref.earned')}</p>
          <p className="text-lg font-semibold tabular-nums text-tg-text">
            {Number(r.earnedUsdc).toFixed(2)} <span className="text-xs font-normal text-tg-hint">pUSD</span>
          </p>
        </div>
      </div>
      <button type="button" onClick={() => void copy()} className="w-full rounded-lg bg-tg-bg p-2 text-left">
        <span className="block text-[10px] uppercase tracking-wide text-tg-hint">
          {t('ref.link')} · {copied ? t('common.copied') : t('common.tapToCopy')}
        </span>
        <span className="block break-all font-mono text-xs text-tg-text">{r.link}</span>
      </button>
      <button
        type="button"
        onClick={share}
        className="w-full rounded-lg bg-tg-button py-2 text-sm font-semibold text-tg-buttonText"
      >
        {t('ref.share')}
      </button>
    </section>
  );
}
