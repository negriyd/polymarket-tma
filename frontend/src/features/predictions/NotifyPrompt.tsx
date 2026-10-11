import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api/endpoints';
import { useT } from '@/lib/i18n';
import { askWriteAccess, canAskWriteAccess } from './notifications';

/** "Tell you the result in Telegram?" — shown after a prediction or bet until messages are allowed. */
export function NotifyPrompt() {
  const t = useT();
  const qc = useQueryClient();
  const [busy, setBusy] = useState(false);
  const record = useQuery({ queryKey: ['track-record'], queryFn: api.getTrackRecord, staleTime: 60_000 });

  if (!canAskWriteAccess() || !record.data || record.data.allowsMessages) return null;

  return (
    <div className="flex items-center justify-between gap-2 rounded-lg bg-tg-bg p-2 text-xs text-tg-text">
      <span>🔔 {t('pred.notifyAsk')}</span>
      <button
        type="button"
        disabled={busy}
        onClick={async () => {
          setBusy(true);
          await askWriteAccess();
          setBusy(false);
          void qc.invalidateQueries({ queryKey: ['track-record'] });
        }}
        className="shrink-0 rounded-md bg-tg-button px-3 py-1 font-semibold text-tg-buttonText disabled:opacity-50"
      >
        {t('pred.notifyAllow')}
      </button>
    </div>
  );
}
