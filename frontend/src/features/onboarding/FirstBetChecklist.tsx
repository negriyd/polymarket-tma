import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import clsx from 'clsx';
import { api } from '@/lib/api/endpoints';
import { useT, type MessageKey } from '@/lib/i18n';
import { useWallet } from '@/features/wallet/useWallet';
import { hapticImpact } from '@/lib/telegram/webApp';
import { FIRST_BET_KEY, readChecklistPreview } from './firstBet';

const HIDDEN_KEY = 'firstBetChecklistHidden';
function readFlag(key: string): boolean {
  try {
    return localStorage.getItem(key) === '1';
  } catch {
    return false;
  }
}

interface Step {
  key: MessageKey;
  done: boolean;
  to: string;
}

/**
 * "First bet in 3 minutes": the onboarding steps with their state, so a new user always sees the
 * next thing to do. Shares query keys with the Wallet tab, so it costs no extra requests there.
 */
export function FirstBetChecklist() {
  const t = useT();
  const navigate = useNavigate();
  const w = useWallet();
  const [hidden, setHidden] = useState(() => readFlag(HIDDEN_KEY));
  const [preview] = useState(readChecklistPreview);
  const address = w.authenticated ? w.address : undefined;

  const clob = useQuery({
    queryKey: ['clob-auth', address],
    queryFn: api.clobAuthStatus,
    enabled: !!address,
    staleTime: 60_000,
  });
  const dw = useQuery({
    queryKey: ['deposit-wallet', address],
    queryFn: api.getDepositWallet,
    enabled: !!address,
    staleTime: 15_000,
  });
  const positions = useQuery({
    queryKey: ['positions'],
    queryFn: api.listPositions,
    enabled: !!address,
    staleTime: 30_000,
  });

  if ((hidden && preview == null) || !w.ready) return null;
  // Privy reports "authenticated" a moment before the embedded wallet's address is known.
  if (w.authenticated && !w.address) return null;
  // Wait until every step's data has loaded: rendering on partial data flashes the card for users
  // who have already done everything. A failed query counts as settled (the step shows as not done).
  const settled = (q: { isPending: boolean; fetchStatus: string }) => !q.isPending || q.fetchStatus === 'idle';
  if (preview == null && address && !(settled(clob) && settled(dw) && settled(positions))) return null;

  const balance = dw.data?.collateralBalance != null ? BigInt(dw.data.collateralBalance) : 0n;
  const steps: Step[] = [
    { key: 'onb.wallet', done: !!address, to: '/wallet' },
    { key: 'onb.polymarket', done: clob.data?.configured === true, to: '/wallet' },
    { key: 'onb.activate', done: dw.data?.readyToTrade === true, to: '/wallet' },
    { key: 'onb.fund', done: balance > 0n, to: '/wallet' },
    { key: 'onb.bet', done: readFlag(FIRST_BET_KEY) || (positions.data?.length ?? 0) > 0, to: '/markets' },
  ];
  // A later step can't be done before an earlier one (e.g. stale data): count only the prefix.
  const firstUndone = steps.findIndex((s) => !s.done);
  const doneCount = preview ?? (firstUndone < 0 ? steps.length : firstUndone);
  if (doneCount === steps.length) return null;
  const next = steps[doneCount];

  const hide = () => {
    setHidden(true);
    try {
      localStorage.setItem(HIDDEN_KEY, '1');
    } catch {
      /* storage blocked */
    }
  };

  return (
    <section className="space-y-2 rounded-xl bg-tg-secondary p-4">
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="text-sm font-semibold text-tg-text">
            {t('onb.title')}
            {preview != null && <span className="ml-1 text-[10px] font-normal text-amber-500">{t('onb.previewBadge')}</span>}
          </p>
          <p className="text-[11px] text-tg-hint">{t('onb.progress', { done: doneCount, total: steps.length })}</p>
        </div>
        <button type="button" onClick={hide} className="text-xs text-tg-hint" aria-label={t('common.hide')}>
          ✕
        </button>
      </div>
      <div className="h-1.5 overflow-hidden rounded-full bg-tg-bg">
        <div className="h-full bg-tg-button transition-all" style={{ width: `${(doneCount / steps.length) * 100}%` }} />
      </div>
      <ol className="space-y-1">
        {steps.map((s, i) => (
          <li
            key={s.key}
            className={clsx(
              'flex items-center gap-2 text-sm',
              i < doneCount ? 'text-tg-hint line-through' : i === doneCount ? 'font-medium text-tg-text' : 'text-tg-hint',
            )}
          >
            <span className="w-4 text-center">{i < doneCount ? '✓' : i + 1}</span>
            {t(s.key)}
          </li>
        ))}
      </ol>
      <button
        type="button"
        onClick={() => {
          hapticImpact('light');
          navigate(next.to);
        }}
        className="w-full rounded-lg bg-tg-button py-2 text-sm font-semibold text-tg-buttonText"
      >
        {t(`${next.key}.cta` as MessageKey)}
      </button>
    </section>
  );
}
