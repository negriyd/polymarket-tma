import { useState } from 'react';
import clsx from 'clsx';
import { formatError } from '@/lib/errors';
import { useT } from '@/lib/i18n';
import { hapticImpact } from '@/lib/telegram/webApp';
import { shareMarket } from './share';

interface Props {
  marketKey: string;
  /** Share as "my forecast: Yes/No — what do you think?". */
  side?: 'yes' | 'no';
  /** Icon-only button for headers; otherwise a full-width labelled button. */
  compact?: boolean;
}

export function ShareButton({ marketKey, side, compact }: Props) {
  const t = useT();
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<{ text: string; error: boolean } | null>(null);

  const onClick = async () => {
    hapticImpact('light');
    setBusy(true);
    setNote(null);
    try {
      const result = await shareMarket(marketKey, side);
      if (result === 'link-copied') setNote({ text: t('share.copied'), error: false });
    } catch (e) {
      setNote({ text: formatError(e), error: true });
    } finally {
      setBusy(false);
    }
  };

  const icon = (
    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden>
      <path d="M12 3v12M7 8l5-5 5 5M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );

  return (
    <div className={clsx(compact ? 'relative shrink-0' : 'space-y-1')}>
      <button
        type="button"
        onClick={() => void onClick()}
        disabled={busy}
        aria-label={t('share.button')}
        className={clsx(
          'disabled:opacity-50',
          compact
            ? 'rounded-lg p-1 text-tg-hint'
            : 'flex w-full items-center justify-center gap-2 rounded-xl bg-tg-secondary py-2.5 text-sm font-semibold text-tg-text',
        )}
      >
        {icon}
        {!compact && (side ? t('share.myForecast') : t('share.button'))}
      </button>
      {note && (
        <p
          className={clsx(
            'text-[11px]',
            note.error ? 'text-rose-500' : 'text-emerald-500',
            compact && 'absolute right-0 top-full z-10 mt-1 whitespace-nowrap rounded bg-tg-bg px-2 py-1 shadow',
          )}
        >
          {note.text}
        </p>
      )}
    </div>
  );
}
