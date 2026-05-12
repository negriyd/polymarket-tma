import axios, { AxiosError, AxiosInstance } from 'axios';
import type { ApiError, TokenPair } from './types';
import { useAuthStore } from '@/features/auth/store';

const baseURL = import.meta.env.VITE_API_BASE_URL ?? '';

export const http: AxiosInstance = axios.create({
  baseURL,
  timeout: 15_000,
  withCredentials: false,
});

http.interceptors.request.use((cfg) => {
  const token = useAuthStore.getState().accessToken;
  if (token && cfg.headers) {
    cfg.headers.Authorization = `Bearer ${token}`;
  }
  return cfg;
});

let refreshing: Promise<TokenPair> | null = null;

http.interceptors.response.use(
  (r) => r,
  async (error: AxiosError<ApiError>) => {
    const status = error.response?.status;
    const original = error.config;
    if (status === 401 && original && !(original as { _retried?: boolean })._retried) {
      const refresh = useAuthStore.getState().refreshToken;
      if (refresh) {
        (original as { _retried?: boolean })._retried = true;
        refreshing ??= http
          .post<TokenPair>('/api/auth/refresh', { refreshToken: refresh })
          .then((res) => {
            useAuthStore.getState().setTokens(res.data);
            return res.data;
          })
          .finally(() => {
            refreshing = null;
          });
        try {
          const pair = await refreshing;
          if (original.headers) {
            original.headers.Authorization = `Bearer ${pair.accessToken}`;
          }
          return http.request(original);
        } catch {
          useAuthStore.getState().reset();
        }
      }
    }
    return Promise.reject(error);
  },
);
