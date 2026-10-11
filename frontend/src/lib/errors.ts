import { AxiosError } from 'axios';
import type { ApiError } from '@/lib/api/types';
import { t } from '@/lib/i18n';
import { en } from '@/lib/i18n/locales/en';
import type { MessageKey } from '@/lib/i18n';

interface PrivyErrorShape {
  code?: string;
  type?: string;
}

/**
 * User-facing text for a failed request or wallet action. Backend errors with a known code are
 * translated; others fall back to the server's (English) message.
 */
export function formatError(e: unknown): string {
  if (e instanceof AxiosError) {
    const d = e.response?.data as ApiError | undefined;
    const key = d?.code ? (`error.${d.code}` as MessageKey) : null;
    if (key && key in en) return t(key);
    if (d?.message) return d.message;
    if (e.message) return e.message;
  }
  if (e instanceof Error) {
    const extra = e as Error & PrivyErrorShape;
    const code = extra.code ?? extra.type;
    return code ? `${code}: ${e.message}` : e.message;
  }
  return t('common.somethingWrong');
}
