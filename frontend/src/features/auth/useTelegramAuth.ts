import { useEffect } from 'react';
import { api } from '@/lib/api/endpoints';
import { getInitData } from '@/lib/telegram/webApp';
import { useAuthStore } from './store';

export function useTelegramAuth(): void {
  const status = useAuthStore((s) => s.status);
  const setStatus = useAuthStore((s) => s.setStatus);
  const setTokens = useAuthStore((s) => s.setTokens);

  useEffect(() => {
    if (status !== 'idle') return;
    setStatus('authenticating');
    const initData = getInitData();
    api
      .loginTelegram(initData)
      .then((pair) => setTokens(pair))
      .catch((err: unknown) => {
        const message = err instanceof Error ? err.message : 'auth failed';
        setStatus('error', message);
      });
  }, [status, setStatus, setTokens]);
}
