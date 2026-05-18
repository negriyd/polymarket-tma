import axios, { AxiosError, AxiosInstance, type InternalAxiosRequestConfig } from 'axios';
import type { ApiError, TokenPair } from './types';
import { useAuthStore } from '@/features/auth/store';

const baseURL = import.meta.env.VITE_API_BASE_URL ?? '';

export const http: AxiosInstance = axios.create({
  baseURL,
  timeout: 15_000,
  withCredentials: false,
});

interface RetriableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean;
  /** Set on the refresh call itself to prevent the 401-on-refresh → refresh loop. */
  _skipAuthRefresh?: boolean;
}

http.interceptors.request.use((cfg) => {
  const token = useAuthStore.getState().accessToken;
  if (token && cfg.headers) {
    cfg.headers.Authorization = `Bearer ${token}`;
  }
  return cfg;
});

let refreshing: Promise<TokenPair> | null = null;

function performRefresh(refreshToken: string): Promise<TokenPair> {
  // The dedicated config flag stops the response interceptor from trying to refresh again if THIS call
  // itself returns 401, which would otherwise recurse until we get rate-limited or stack-overflow.
  return http
    .post<TokenPair>(
      '/api/auth/refresh',
      { refreshToken },
      { _skipAuthRefresh: true } as RetriableConfig,
    )
    .then((res) => {
      useAuthStore.getState().setTokens(res.data);
      return res.data;
    });
}

http.interceptors.response.use(
  (r) => r,
  async (error: AxiosError<ApiError>) => {
    const status = error.response?.status;
    const original = error.config as RetriableConfig | undefined;

    if (status === 401) {
      const reason = (error.response?.headers?.['x-auth-error'] as string | undefined) ?? null;
      if (reason) console.warn('401 from', original?.url, '— X-Auth-Error:', reason);
    }

    if (status !== 401 || !original) return Promise.reject(error);

    // Don't try to refresh if the failing call IS the refresh / login itself: there is nothing to
    // recover from on the client and we'd loop indefinitely.
    if (original._skipAuthRefresh) return Promise.reject(error);
    if (
      original.url?.includes('/api/auth/refresh') ||
      original.url?.includes('/api/auth/telegram')
    ) {
      return Promise.reject(error);
    }
    if (original._retried) return Promise.reject(error);

    const refresh = useAuthStore.getState().refreshToken;
    if (!refresh) {
      useAuthStore.getState().setStatus('idle');
      return Promise.reject(error);
    }

    original._retried = true;
    refreshing ??= performRefresh(refresh).finally(() => {
      refreshing = null;
    });

    try {
      const pair = await refreshing;
      if (original.headers) {
        original.headers.Authorization = `Bearer ${pair.accessToken}`;
      }
      return http.request(original);
    } catch (refreshErr) {
      // Refresh token is gone/expired/revoked — drop our cached auth so AuthGate re-runs
      // useTelegramAuth on the next render and the user gets a fresh session from initData.
      useAuthStore.getState().reset();
      console.warn('Refresh failed, session reset:', refreshErr);
      return Promise.reject(error);
    }
  },
);
