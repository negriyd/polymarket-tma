import { create } from 'zustand';
import { tg } from '@/lib/telegram/webApp';
import { DEFAULT_LOCALE, isLocale, matchLocale, type Locale } from './core';

const STORAGE_KEY = 'locale';

function readStored(): Locale | null {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    return isLocale(v) ? v : null;
  } catch {
    return null;
  }
}

/** Saved choice, then the Telegram client language, then the browser, then English. */
export function detectLocale(): Locale {
  let telegram: string | undefined;
  try {
    telegram = tg.initDataUnsafe?.user?.language_code;
  } catch {
    /* opened outside Telegram */
  }
  const browser = typeof navigator === 'undefined' ? undefined : navigator.language;
  return readStored() ?? matchLocale(telegram) ?? matchLocale(browser) ?? DEFAULT_LOCALE;
}

interface LocaleState {
  locale: Locale;
  setLocale: (locale: Locale) => void;
}

export const useLocaleStore = create<LocaleState>((set) => ({
  locale: detectLocale(),
  setLocale: (locale) => {
    try {
      localStorage.setItem(STORAGE_KEY, locale);
    } catch {
      /* storage blocked: the choice lasts for this session */
    }
    document.documentElement.lang = locale;
    set({ locale });
  },
}));
