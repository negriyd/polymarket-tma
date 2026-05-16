import { describe, expect, it } from 'vitest';
import { AxiosError } from 'axios';
import { formatAuthError } from '@/features/auth/formatAuthError';

describe('formatAuthError', () => {
  it('uses backend ApiError message and code', () => {
    // AxiosResponse typings are strict; cast for a minimal fixture.
    const response = {
      status: 401,
      statusText: 'Unauthorized',
      data: { code: 'INIT_DATA_HASH_MISMATCH', message: 'initData hash mismatch' },
      headers: {},
      config: {} as import('axios').InternalAxiosRequestConfig,
    } as import('axios').AxiosResponse;

    const err = new AxiosError(undefined, undefined, undefined, undefined, response);

    expect(formatAuthError(err)).toContain('initData hash mismatch');
    expect(formatAuthError(err)).toContain('INIT_DATA_HASH_MISMATCH');
  });

  it('falls back for network-ish failures', () => {
    expect(formatAuthError(new Error('fail'))).toBe('fail');
  });
});
