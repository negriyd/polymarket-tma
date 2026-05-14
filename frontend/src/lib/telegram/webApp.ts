import WebApp from '@twa-dev/sdk';

export type ColorScheme = 'light' | 'dark';

export interface TgUserSummary {
  id: number;
  username?: string;
  firstName?: string;
  lastName?: string;
  photoUrl?: string;
  languageCode?: string;
  isPremium: boolean;
}

const DEV_MOCK_INIT_DATA = `mock-${Math.floor(Math.random() * 1_000_000)}`;

export const tg = WebApp;

/** Resolve initData with a development fallback when the page is opened outside Telegram. */
export function getInitData(): string {
  if (typeof window === 'undefined') return DEV_MOCK_INIT_DATA;
  return WebApp.initData && WebApp.initData.length > 0 ? WebApp.initData : DEV_MOCK_INIT_DATA;
}

export function applyTelegramTheme(): ColorScheme {
  const scheme = (WebApp.colorScheme ?? 'light') as ColorScheme;
  const root = document.documentElement;
  root.classList.toggle('dark', scheme === 'dark');

  const params = WebApp.themeParams ?? {};
  const setVar = (name: string, value?: string) => {
    if (value) document.documentElement.style.setProperty(name, value);
  };
  setVar('--tg-bg', params.bg_color);
  setVar('--tg-text', params.text_color);
  setVar('--tg-hint', params.hint_color);
  setVar('--tg-link', params.link_color);
  setVar('--tg-button', params.button_color);
  setVar('--tg-button-text', params.button_text_color);
  setVar('--tg-secondary-bg', params.secondary_bg_color);
  return scheme;
}

/**
 * Signals to Telegram that the Mini App scripts have loaded — the client hides its loading spinner.
 * Must run early (sync in {@code main.tsx}); React {@code useEffect} can be too late if the tree
 * blocks/suspends on first paint.
 */
export function announceTelegramReady(): void {
  try {
    WebApp.ready();
    WebApp.expand();
  } catch {
    /* opened outside Telegram */
  }
}

export function bootstrapTelegram(): void {
  announceTelegramReady();
  try {
    applyTelegramTheme();
    WebApp.onEvent('themeChanged', applyTelegramTheme);
  } catch {
    /* SDK may not expose all APIs outside Telegram WebView */
  }
}

export function hapticImpact(style: 'light' | 'medium' | 'heavy' = 'light'): void {
  try {
    WebApp.HapticFeedback?.impactOccurred(style);
  } catch {
    /* ignore */
  }
}
