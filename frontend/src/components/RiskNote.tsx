import { useT } from '@/lib/i18n';

/** 18+ and risk notice; the app is an independent Polymarket client. */
export function RiskNote({ short }: { short?: boolean }) {
  const t = useT();
  return <p className="text-[10px] leading-snug text-tg-hint">{t(short ? 'risk.short' : 'risk.full')}</p>;
}
