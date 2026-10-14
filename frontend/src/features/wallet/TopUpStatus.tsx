import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api/endpoints';
import { useT } from '@/lib/i18n';
import { useWallet } from './useWallet';
import { IN_FLIGHT, depositsRefetch, formatAmount } from './deposits';

/** A top-up credited this recently still gets a "ready to bet" nudge on Home. */
const RECENT_MS = 60 * 60_000;

/** Home banner for a top-up on its way through the bridge, or one that just landed. */
export function TopUpStatus() {
  const t = useT();
  const w = useWallet();
  const qc = useQueryClient();
  const enabled = w.authenticated && !!w.address;
  const deposits = useQuery({
    queryKey: ['bridge-deposits'],
    queryFn: api.listBridgeDeposits,
    enabled,
    refetchInterval: depositsRefetch,
  });
  const funding = useQuery({ queryKey: ['funding'], queryFn: api.getFunding, enabled, staleTime: 10 * 60_000 });

  const list = deposits.data ?? [];
  const moving = list.find((d) => IN_FLIGHT.has(d.status));
  const landed = list.find(
    (d) => d.status === 'COMPLETED' && d.createdTimeMs != null && Date.now() - d.createdTimeMs < RECENT_MS,
  );
  const completedCount = list.filter((d) => d.status === 'COMPLETED').length;
  useEffect(() => {
    if (completedCount > 0) qc.invalidateQueries({ queryKey: ['deposit-wallet'] });
  }, [completedCount, qc]);

  const shown = moving ?? landed;
  if (!enabled || !shown) return null;
  const amount = formatAmount(shown, funding.data?.options ?? []) || '…';

  return moving ? (
    <Link to="/wallet" className="block rounded-2xl bg-amber-500/10 p-3 ring-1 ring-amber-500/20">
      <p className="text-sm font-semibold text-tg-text">⏳ {t('home.topUpOnTheWay', { amount })}</p>
      <p className="mt-0.5 text-xs text-tg-hint">{t('home.topUpEta')}</p>
    </Link>
  ) : (
    <div className="rounded-2xl bg-emerald-500/10 p-3 ring-1 ring-emerald-500/20">
      <p className="text-sm font-semibold text-tg-text">✓ {t('home.topUpDone', { amount })}</p>
      <Link to="/markets" className="mt-1 inline-block text-sm font-medium text-tg-button">
        {t('home.topUpBet')} →
      </Link>
    </div>
  );
}
