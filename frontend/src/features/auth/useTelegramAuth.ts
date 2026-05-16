import { useEffect } from 'react';
import { api } from '@/lib/api/endpoints';
import { getInitData } from '@/lib/telegram/webApp';
import { formatAuthError } from './formatAuthError';
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
        setStatus('error', formatAuthError(err));
      });
  }, [status, setStatus, setTokens]);
}
