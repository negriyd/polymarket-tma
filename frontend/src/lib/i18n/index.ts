import { useCallback } from 'react';
import { translate, type Locale, type MessageKey, type Vars } from './core';
import { useLocaleStore } from './store';

export { LOCALES, type Locale, type MessageKey } from './core';
export { useLocaleStore } from './store';

export type TFunction = (key: MessageKey, vars?: Vars) => string;

/** Translator for the current locale; components re-render when the user switches language. */
export function useT(): TFunction {
  const locale = useLocaleStore((s) => s.locale);
  return useCallback((key, vars) => translate(locale, key, vars), [locale]);
}

export function useLocale(): Locale {
  return useLocaleStore((s) => s.locale);
}

/** For code outside React (mutation helpers, error formatting). */
export function t(key: MessageKey, vars?: Vars): string {
  return translate(useLocaleStore.getState().locale, key, vars);
}
export { Trans } from './Trans';

/** Polymarket outcomes are English; translate the common binary ones, keep names ("Texas") as-is. */
export function outcomeText(raw: string | null | undefined, tr: TFunction): string {
  const v = (raw ?? '').trim().toLowerCase();
  if (v === 'yes') return tr('common.yes');
  if (v === 'no') return tr('common.no');
  return raw ?? '';
}
