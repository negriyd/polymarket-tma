import { AxiosError } from 'axios';
import type { ApiError } from '@/lib/api/types';

/**
 * Surfaces backend {@link ApiError} body; Axios default only exposes HTTP status strings.
 */
export function formatAuthError(err: unknown): string {
  if (err instanceof AxiosError) {
    const data = err.response?.data as ApiError | undefined;
    const status = err.response?.status;

    if (data?.message) {
      return data.code ? `${data.message} [${data.code}]` : data.message;
    }
    if (status === 0 || err.code === 'ECONNABORTED') {
      return 'Request timed out — check VPN and that VITE_API_BASE_URL reaches your backend.';
    }
    if (status === 401 || status === 403) {
      return 'Unauthorized — BOT_TOKEN may not match your Mini App bot, or Telegram initData is invalid/expired [HTTP '
        + String(status ?? '') + ']';
    }
    if (typeof status === 'number' && status >= 400) {
      return `HTTP ${status} ${err.response?.statusText ?? ''}`.trim();
    }
    return err.message || 'Cannot reach API — verify VITE_API_BASE_URL.';
  }

  return err instanceof Error ? err.message : 'Authentication failed';
}
