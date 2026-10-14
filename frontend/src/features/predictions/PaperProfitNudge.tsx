import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api/endpoints';
import { useT } from '@/lib/i18n';
import { FIRST_BET_KEY } from '@/features/onboarding/firstBet';

/** Enough calls to be more than luck before we suggest real money. */
const MIN_RESOLVED = 3;

function hasBet(): boolean {
  try {
    return localStorage.getItem(FIRST_BET_KEY) === '1';
  } catch {
    return false;
  }
}

/**
 * Home: someone who keeps calling markets right for free but never bet sees what $5 on each of
 * their predictions would have made. Only when that number is positive.
 */
export function PaperProfitNudge() {
  const t = useT();
  const record = useQuery({ queryKey: ['track-record'], queryFn: api.getTrackRecord, staleTime: 60_000 });
  const r = record.data;
  if (!r || hasBet()) return null;
  const resolved = r.predictions.correct + r.predictions.wrong;
  const betsResolved = r.bets.correct + r.bets.wrong;
  if (resolved < MIN_RESOLVED || betsResolved > 0 || !(r.paperProfit > 0)) return null;

  return (
    <div className="rounded-2xl bg-tg-secondary p-4">
      <p className="text-sm font-semibold text-tg-text">
        🎯 {t('nudge.calls', { correct: r.predictions.correct, resolved })}
      </p>
      <p className="mt-1 text-sm text-tg-text">{t('nudge.paper', { profit: r.paperProfit.toFixed(2) })}</p>
      <Link
        to="/markets"
        className="mt-3 inline-block rounded-xl bg-tg-button px-4 py-2 text-sm font-semibold text-tg-buttonText"
      >
        {t('pred.realBet')}
      </Link>
    </div>
  );
}
