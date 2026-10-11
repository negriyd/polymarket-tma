import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import clsx from 'clsx';
import { api } from '@/lib/api/endpoints';
import type { Market } from '@/lib/api/types';
import { formatError } from '@/lib/errors';
import { outcomeText, useT } from '@/lib/i18n';
import { isTradable, marketState } from '@/lib/marketState';
import { hapticImpact } from '@/lib/telegram/webApp';
import { NotifyPrompt } from './NotifyPrompt';

/** "Прогноз без денег" on a market page: pick Yes/No for free, see whether it came true. */
export function PredictionBlock({ market }: { market: Market }) {
  const t = useT();
  const qc = useQueryClient();
  const cid = market.conditionId.toLowerCase();
  const state = marketState(market);
  const open = isTradable(state);

  const mine = useQuery({
    queryKey: ['predictions', cid],
    queryFn: () => api.listPredictions(cid),
    staleTime: 30_000,
  });
  const prediction = mine.data?.[0];

  const predict = useMutation({
    mutationFn: (side: 'yes' | 'no') => api.predict({ marketKey: market.id ?? market.conditionId, side }),
    onSuccess: () => {
      hapticImpact('medium');
      void qc.invalidateQueries({ queryKey: ['predictions'] });
      void qc.invalidateQueries({ queryKey: ['track-record'] });
    },
  });

  if (!open && !prediction) return null;

  const picked = prediction ? outcomeText(prediction.outcomeLabel, t) : null;
  const cents = prediction?.price != null ? Math.round(Number(prediction.price) * 100) : null;
  const pickedYes = prediction != null && prediction.outcomeLabel.toLowerCase() === 'yes';
  const pickedNo = prediction != null && prediction.outcomeLabel.toLowerCase() === 'no';

  return (
    <section className="space-y-2 rounded-xl bg-tg-secondary p-3">
      <div>
        <h2 className="text-sm font-semibold text-tg-text">{t('pred.title')}</h2>
        {!prediction && <p className="text-[11px] text-tg-hint">{t('pred.hint')}</p>}
      </div>

      {prediction && (
        <p className="text-sm text-tg-text">
          {prediction.correct == null
            ? t('pred.yours', { outcome: picked ?? '', cents: cents ?? '—' })
            : prediction.correct
              ? t('pred.cameTrue', { outcome: picked ?? '' })
              : t('pred.notTrue', { outcome: picked ?? '' })}
        </p>
      )}

      {prediction?.correct === true && cents != null && cents > 0 && (
        <div className="flex items-center justify-between gap-2 rounded-lg bg-emerald-500/10 p-2 text-xs text-tg-text">
          <span>{t('pred.wouldWin', { win: (5 / (cents / 100)).toFixed(2) })}</span>
          <Link to="/markets" className="shrink-0 rounded-md bg-tg-button px-3 py-1 font-semibold text-tg-buttonText">
            {t('pred.realBet')}
          </Link>
        </div>
      )}

      {open && (
        <div className="grid grid-cols-2 gap-2">
          {(['yes', 'no'] as const).map((side) => {
            const selected = side === 'yes' ? pickedYes : pickedNo;
            return (
              <button
                key={side}
                type="button"
                disabled={predict.isPending || selected}
                onClick={() => predict.mutate(side)}
                className={clsx(
                  'rounded-lg py-2 text-sm font-semibold disabled:cursor-default',
                  side === 'yes' ? 'text-emerald-500' : 'text-rose-500',
                  selected ? (side === 'yes' ? 'bg-emerald-500/25 ring-2 ring-emerald-500' : 'bg-rose-500/25 ring-2 ring-rose-500') : 'bg-tg-bg',
                )}
              >
                {t('pred.pick', { outcome: t(side === 'yes' ? 'common.yes' : 'common.no') })}
              </button>
            );
          })}
        </div>
      )}
      {predict.isError && <p className="text-xs text-rose-500">{formatError(predict.error)}</p>}
      {prediction && prediction.correct == null && <NotifyPrompt />}
    </section>
  );
}
