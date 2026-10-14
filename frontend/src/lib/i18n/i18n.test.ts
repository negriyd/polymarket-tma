import { describe, expect, it } from 'vitest';
import { LOCALES, matchLocale, translate, type Locale, type Message, type MessageKey } from './core';
import { en } from './locales/en';

const placeholders = (m: Message): string[] => {
  const texts = typeof m === 'string' ? [m] : Object.values(m).filter((v): v is string => !!v);
  return [...new Set(texts.flatMap((s) => [...s.matchAll(/\{(\w+)\}/g)].map((x) => x[1])))].sort();
};

describe('i18n', () => {
  const keys = Object.keys(en) as MessageKey[];

  it.each(Object.keys(LOCALES) as Locale[])('%s uses the same placeholders as English', (locale) => {
    for (const key of keys) {
      expect([key, placeholders(LOCALES[locale].messages[key])]).toEqual([key, placeholders(en[key])]);
    }
  });

  it('interpolates variables and keeps unknown slots for <Trans>', () => {
    expect(translate('en', 'home.greeting', { name: 'Ann' })).toBe('Hi Ann!');
    expect(translate('en', 'trade.connectFirst')).toBe('Connect your wallet on the {wallet} tab first.');
  });

  it('picks plural forms per language', () => {
    expect(translate('en', 'dw.approvalsPending', { count: 1 })).toBe('1 approval pending');
    expect(translate('en', 'dw.approvalsPending', { count: 10 })).toBe('10 approvals pending');
    expect(translate('ru', 'pos.endsIn', { count: 1 })).toBe('через 1 день');
    expect(translate('ru', 'pos.endsIn', { count: 3 })).toBe('через 3 дня');
    expect(translate('ru', 'pos.endsIn', { count: 5 })).toBe('через 5 дней');
    expect(translate('uk', 'pos.endsIn', { count: 22 })).toBe('за 22 дні');
    expect(translate('zh', 'pos.endsIn', { count: 2 })).toBe('2 天后结束');
    expect(translate('pl', 'pos.endsIn', { count: 1 })).toBe('kończy się za 1 dzień');
    expect(translate('pl', 'pos.endsIn', { count: 5 })).toBe('kończy się za 5 dni');
  });

  it('maps browser and Telegram language tags', () => {
    expect(matchLocale('pt-BR')).toBe('pt');
    expect(matchLocale('zh-Hans')).toBe('zh');
    expect(matchLocale('UK')).toBe('uk');
    expect(matchLocale('de-AT')).toBe('de');
    expect(matchLocale('fil')).toBe('tl');
    expect(matchLocale('ko')).toBeNull();
    expect(matchLocale(undefined)).toBeNull();
  });
});
