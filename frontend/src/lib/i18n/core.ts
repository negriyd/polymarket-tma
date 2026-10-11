import { en } from './locales/en';
import { ru } from './locales/ru';
import { uk } from './locales/uk';
import { es } from './locales/es';
import { pt } from './locales/pt';
import { zh } from './locales/zh';

/** A message, or its plural forms chosen by `Intl.PluralRules` from the `count` variable. */
export type Plural = { one?: string; few?: string; many?: string; other: string };
export type Message = string | Plural;
export type MessageKey = keyof typeof en;
/** Every locale must translate every key — a missing one is a compile error. */
export type Messages = Record<MessageKey, Message>;
export type Vars = Record<string, string | number>;

export const LOCALES = {
  en: { name: 'English', messages: en },
  ru: { name: 'Русский', messages: ru },
  uk: { name: 'Українська', messages: uk },
  es: { name: 'Español', messages: es },
  pt: { name: 'Português', messages: pt },
  zh: { name: '中文', messages: zh },
} satisfies Record<string, { name: string; messages: Messages }>;

export type Locale = keyof typeof LOCALES;
export const DEFAULT_LOCALE: Locale = 'en';

export function isLocale(value: unknown): value is Locale {
  return typeof value === 'string' && Object.prototype.hasOwnProperty.call(LOCALES, value);
}

/** Maps a BCP 47 tag ("pt-BR", "zh-Hans", "ru") to a supported locale, or null. */
export function matchLocale(tag?: string | null): Locale | null {
  if (!tag) return null;
  const base = tag.toLowerCase().split(/[-_]/)[0];
  return isLocale(base) ? base : null;
}

const pluralRules = new Map<Locale, Intl.PluralRules>();

function pluralForm(locale: Locale, msg: Plural, count: number): string {
  let rules = pluralRules.get(locale);
  if (!rules) {
    rules = new Intl.PluralRules(locale);
    pluralRules.set(locale, rules);
  }
  const form = rules.select(count) as keyof Plural;
  return msg[form] ?? msg.other;
}

export function translate(locale: Locale, key: MessageKey, vars?: Vars): string {
  const msg: Message = LOCALES[locale].messages[key] ?? en[key];
  const text =
    typeof msg === 'string' ? msg : pluralForm(locale, msg, Number(vars?.count ?? 0));
  if (!vars) return text;
  return text.replace(/\{(\w+)\}/g, (whole, name: string) =>
    vars[name] != null ? String(vars[name]) : whole,
  );
}
