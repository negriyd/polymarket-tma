import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { tg } from '@/lib/telegram/webApp';
import { parseStartParam } from './share';
import { refCodeOf, rememberAuthorRef } from '@/features/partners/authorRef';

let consumed = false;

/**
 * Opens the market from a shared t.me/<bot>?startapp=m_<id> link (or the Wallet tab / a partner's
 * leaderboard), once per launch, and remembers the partner whose link it was.
 */
export function DeepLinkHandler() {
  const navigate = useNavigate();
  useEffect(() => {
    if (consumed) return;
    consumed = true;
    let param: string | undefined;
    try {
      param = tg.initDataUnsafe?.start_param;
    } catch {
      /* opened outside Telegram */
    }
    const ref = refCodeOf(param);
    rememberAuthorRef(ref);
    const tokens = (param ?? '').split('_');
    const link = parseStartParam(param);
    // `lb` = a partner's weekly leaderboard.
    if (ref && tokens.includes('lb')) navigate(`/leaderboard/${ref}`);
    else if (link) navigate(`/markets/${link.marketId}`, { state: { friendPick: link.friendPick } });
    // `w` = open the Wallet tab (e.g. "Claim winnings" in a result message).
    else if (tokens.includes('w')) navigate('/wallet');
  }, [navigate]);
  return null;
}
