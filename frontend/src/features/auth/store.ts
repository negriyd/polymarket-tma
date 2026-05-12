import { create } from 'zustand';
import type { TokenPair, UserInfo } from '@/lib/api/types';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: UserInfo | null;
  status: 'idle' | 'authenticating' | 'authenticated' | 'error';
  error: string | null;
  setStatus: (status: AuthState['status'], error?: string | null) => void;
  setTokens: (pair: TokenPair) => void;
  reset: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  refreshToken: null,
  user: null,
  status: 'idle',
  error: null,
  setStatus: (status, error = null) => set({ status, error }),
  setTokens: ({ accessToken, refreshToken, user }) =>
    set({ accessToken, refreshToken, user, status: 'authenticated', error: null }),
  reset: () => set({ accessToken: null, refreshToken: null, user: null, status: 'idle', error: null }),
}));
