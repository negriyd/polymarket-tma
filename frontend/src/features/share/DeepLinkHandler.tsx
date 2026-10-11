import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { tg } from '@/lib/telegram/webApp';
import { parseStartParam } from './share';

let consumed = false;

/** Opens the market from a shared t.me/<bot>?startapp=m_<id> link, once per launch. */
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
    const link = parseStartParam(param);
    if (link) navigate(`/markets/${link.marketId}`, { state: { friendPick: link.friendPick } });
    // `w` = open the Wallet tab (e.g. "Claim winnings" in a result message).
    else if ((param ?? '').split('_').includes('w')) navigate('/wallet');
  }, [navigate]);
  return null;
}
