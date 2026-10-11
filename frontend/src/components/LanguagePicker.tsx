import { useQueryClient } from '@tanstack/react-query';
import { LOCALES, useLocaleStore, useT, type Locale } from '@/lib/i18n';

export function LanguagePicker() {
  const t = useT();
  const locale = useLocaleStore((s) => s.locale);
  const setLocale = useLocaleStore((s) => s.setLocale);
  const qc = useQueryClient();
  return (
    <label className="inline-flex items-center gap-1 rounded-full bg-tg-secondary px-2.5 py-1 text-xs text-tg-text">
      <span aria-hidden>🌐</span>
      <span className="sr-only">{t('common.language')}</span>
      <select
        value={locale}
        onChange={(e) => {
          setLocale(e.target.value as Locale);
          // Market titles come translated from the server: refetch everything in the new language.
          void qc.invalidateQueries();
        }}
        className="bg-transparent text-xs text-tg-text outline-none"
      >
        {(Object.keys(LOCALES) as Locale[]).map((code) => (
          <option key={code} value={code}>
            {LOCALES[code].name}
          </option>
        ))}
      </select>
    </label>
  );
}
