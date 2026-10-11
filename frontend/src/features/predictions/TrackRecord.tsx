import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '@/lib/api/endpoints';
import { outcomeText, useT } from '@/lib/i18n';
import { askWriteAccess, canAskWriteAccess } from './notifications';

/** Wallet tab: "Predictions 7 ✓ · 2 ✗ · 78%", "Bets 3 ✓ · 1 ✗", result messages switch, my predictions. */
export function TrackRecord() {
  const t = useT();
  const qc = useQueryClient();
  const record = useQuery({ queryKey: ['track-record'], queryFn: api.getTrackRecord, staleTime: 60_000 });
  const predictions = useQuery({ queryKey: ['predictions', 'all'], queryFn: () => api.listPredictions(), staleTime: 60_000 });

  const toggle = useMutation({
    mutationFn: async (enable: boolean) => {
      if (enable && canAskWriteAccess()) {
        await askWriteAccess();
        return;
      }
      await api.setResultNotifications(enable);
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ['track-record'] }),
  });

  const r = record.data;
  if (!r) return null;
  const p = r.predictions;
  const resolved = p.correct + p.wrong;
  const hasAnything = resolved + p.pending + r.bets.correct + r.bets.wrong > 0;
  const list = predictions.data ?? [];

  return (
    <section className="space-y-2 rounded-xl bg-tg-secondary p-4">
      <p className="text-xs uppercase tracking-wide text-tg-hint">{t('pred.record')}</p>
      {hasAnything ? (
        <div className="grid grid-cols-2 gap-2">
          <div className="rounded-lg bg-tg-bg px-3 py-2">
            <p className="text-[10px] uppercase tracking-wide text-tg-hint">{t('pred.predictions')}</p>
            <p className="text-sm font-semibold tabular-nums text-tg-text">
              {p.correct} ✓ · {p.wrong} ✗{resolved > 0 ? ` · ${Math.round((p.correct / resolved) * 100)}%` : ''}
            </p>
            {p.pending > 0 && <p className="text-[10px] text-tg-hint">{t('pred.pending', { n: p.pending })}</p>}
          </div>
          <div className="rounded-lg bg-tg-bg px-3 py-2">
            <p className="text-[10px] uppercase tracking-wide text-tg-hint">{t('pred.bets')}</p>
            <p className="text-sm font-semibold tabular-nums text-tg-text">
              {r.bets.correct} ✓ · {r.bets.wrong} ✗
            </p>
          </div>
        </div>
      ) : (
        <p className="text-xs text-tg-hint">{t('pred.empty')}</p>
      )}

      <label className="flex items-center justify-between gap-2 text-xs text-tg-text">
        <span>🔔 {t('pred.notifySwitch')}</span>
        <button
          type="button"
          role="switch"
          aria-checked={r.allowsMessages}
          disabled={toggle.isPending}
          onClick={() => toggle.mutate(!r.allowsMessages)}
          className={
            r.allowsMessages
              ? 'rounded-full bg-tg-button px-3 py-1 font-medium text-tg-buttonText'
              : 'rounded-full bg-tg-bg px-3 py-1 font-medium text-tg-hint'
          }
        >
          {r.allowsMessages ? t('common.on') : t('common.off')}
        </button>
      </label>

      {list.length > 0 && (
        <ul className="space-y-1 pt-1">
          {list.slice(0, 10).map((x) => (
            <li key={x.conditionId}>
              <Link to={`/markets/${x.marketId}`} className="flex items-center justify-between gap-2 text-xs">
                <span className="min-w-0 truncate text-tg-text">{x.question}</span>
                <span
                  className={
                    x.correct == null ? 'shrink-0 text-tg-hint' : x.correct ? 'shrink-0 text-emerald-500' : 'shrink-0 text-rose-500'
                  }
                >
                  {outcomeText(x.outcomeLabel, t)} {x.correct == null ? '⏳' : x.correct ? '✓' : '✗'}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
