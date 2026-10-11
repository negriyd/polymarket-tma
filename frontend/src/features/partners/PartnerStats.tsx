import type { PartnerFunnel } from '@/lib/api/types';
import { useT, type MessageKey } from '@/lib/i18n';
import { usd } from './partnerLinks';

/** Opens → sign-ups → wallet → bettors, then volume and earnings, as compact tiles. */
export function PartnerStats({ totals }: { totals: PartnerFunnel }) {
  const t = useT();
  const tiles: [MessageKey, string][] = [
    ['pt.opens', String(totals.launches)],
    ['pt.users', String(totals.users)],
    ['pt.wallet', String(totals.wallet)],
    ['pt.bettors', String(totals.bet)],
    ['pt.volume', usd(totals.volumeUsdc)],
    ['pt.earned', usd(totals.earnedUsdc)],
  ];
  return (
    <div className="grid grid-cols-3 gap-1.5">
      {tiles.map(([key, value]) => (
        <div key={key} className="rounded-lg bg-tg-bg px-2 py-1.5">
          <p className="text-[10px] uppercase tracking-wide text-tg-hint">{t(key)}</p>
          <p className="text-sm font-semibold tabular-nums text-tg-text">{value}</p>
        </div>
      ))}
    </div>
  );
}
