import { useEffect, useMemo, useRef, useState, type PointerEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { api } from '@/lib/api/endpoints';
import { useLocale, useT, type MessageKey } from '@/lib/i18n';

const INTERVALS = ['1h', '6h', '1d', '1w', '1m', 'max'] as const;
type Interval = (typeof INTERVALS)[number];

const HEIGHT = 170;
const PAD = { top: 8, right: 34, bottom: 18, left: 2 };

interface Props {
  conditionId: string;
  /** "Yes" outcome token: the chart shows the probability of Yes, like polymarket.com. */
  tokenId: string;
  /** Current price, shown before history loads and as the headline when not scrubbing. */
  currentPrice?: number;
}

function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver(([entry]) => setWidth(Math.round(entry.contentRect.width)));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  return [ref, width] as const;
}

/** Gridline levels (fractions) spanning [lo, hi] at a round step. */
function gridLevels(lo: number, hi: number): number[] {
  const span = hi - lo;
  const step = span > 0.5 ? 0.25 : span > 0.2 ? 0.1 : span > 0.08 ? 0.05 : 0.02;
  const out: number[] = [];
  for (let v = Math.ceil(lo / step) * step; v <= hi + 1e-9; v += step) out.push(Number(v.toFixed(4)));
  return out;
}

/** Probability of "Yes" over time for one market, with an interval picker and touch scrubbing. */
export function PriceChart({ conditionId, tokenId, currentPrice }: Props) {
  const t = useT();
  const locale = useLocale();
  const [interval, setInterval] = useState<Interval>('1w');
  const [hover, setHover] = useState<number | null>(null);
  const [boxRef, width] = useWidth<HTMLDivElement>();

  const history = useQuery({
    queryKey: ['history', tokenId, interval],
    queryFn: () => api.getHistory(conditionId, tokenId, interval),
    staleTime: 60_000,
  });
  const points = useMemo(
    () => (history.data?.history ?? []).filter((p) => Number.isFinite(p.p) && Number.isFinite(p.t)),
    [history.data],
  );

  const chart = useMemo(() => {
    if (points.length < 2 || width < 50) return null;
    const t0 = points[0].t;
    const t1 = points[points.length - 1].t;
    const prices = points.map((p) => p.p);
    const lo = Math.max(0, Math.min(...prices) - 0.03);
    const hi = Math.min(1, Math.max(...prices) + 0.03);
    const w = width - PAD.left - PAD.right;
    const h = HEIGHT - PAD.top - PAD.bottom;
    const x = (ts: number) => PAD.left + (t1 === t0 ? w : ((ts - t0) / (t1 - t0)) * w);
    const y = (p: number) => PAD.top + (hi === lo ? h / 2 : (1 - (p - lo) / (hi - lo)) * h);
    // Step line: a price holds until the next trade, as on polymarket.com.
    let d = `M${x(points[0].t).toFixed(1)},${y(points[0].p).toFixed(1)}`;
    for (let i = 1; i < points.length; i++) {
      d += `H${x(points[i].t).toFixed(1)}V${y(points[i].p).toFixed(1)}`;
    }
    return { d, x, y, levels: gridLevels(lo, hi), t0, t1 };
  }, [points, width]);

  const shortInterval = interval === '1h' || interval === '6h' || interval === '1d';
  const fmtTime = (sec: number) =>
    new Date(sec * 1000).toLocaleString(
      locale,
      shortInterval ? { hour: '2-digit', minute: '2-digit' } : { month: 'short', day: 'numeric' },
    );

  const shown = hover != null ? points[hover] : null;
  const headline = shown?.p ?? (points.length ? points[points.length - 1].p : currentPrice);
  const first = points[0]?.p;
  const change = headline != null && first != null ? Math.round((headline - first) * 100) : null;

  const onMove = (e: PointerEvent<SVGSVGElement>) => {
    if (!chart || points.length === 0) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const px = e.clientX - rect.left;
    let best = 0;
    for (let i = 1; i < points.length; i++) {
      if (Math.abs(chart.x(points[i].t) - px) < Math.abs(chart.x(points[best].t) - px)) best = i;
    }
    setHover(best);
  };

  return (
    <section className="rounded-xl bg-tg-secondary p-3">
      <div className="flex items-baseline justify-between gap-2">
        <p className="text-lg font-semibold tabular-nums text-tg-link">
          {headline == null ? '—' : t('chart.chance', { pct: Math.round(headline * 100) })}
          {change != null && change !== 0 && hover == null && (
            <span className={clsx('ml-2 text-xs', change > 0 ? 'text-emerald-500' : 'text-rose-500')}>
              {change > 0 ? '▲' : '▼'}
              {Math.abs(change)}%
            </span>
          )}
        </p>
        {shown && <p className="text-[11px] text-tg-hint">{fmtTime(shown.t)}</p>}
      </div>

      <div ref={boxRef} className="relative mt-1 h-[170px] w-full">
        {history.isLoading && (
          <p className="absolute inset-0 flex items-center justify-center text-xs text-tg-hint">
            {t('common.loading')}
          </p>
        )}
        {!history.isLoading && !chart && (
          <p className="absolute inset-0 flex items-center justify-center text-xs text-tg-hint">
            {history.isError ? t('chart.failed') : t('chart.empty')}
          </p>
        )}
        {chart && (
          <svg
            width={width}
            height={HEIGHT}
            className="touch-none select-none"
            onPointerMove={onMove}
            onPointerDown={onMove}
            onPointerLeave={() => setHover(null)}
            onPointerUp={(e) => e.pointerType !== 'mouse' && setHover(null)}
          >
            {chart.levels.map((lv) => (
              <g key={lv}>
                <line
                  x1={PAD.left}
                  x2={width - PAD.right}
                  y1={chart.y(lv)}
                  y2={chart.y(lv)}
                  className="stroke-tg-hint/20"
                  strokeDasharray="2 4"
                />
                <text x={width - 2} y={chart.y(lv) + 3} textAnchor="end" className="fill-tg-hint text-[10px]">
                  {Math.round(lv * 100)}%
                </text>
              </g>
            ))}
            {[chart.t0, (chart.t0 + chart.t1) / 2, chart.t1].map((ts, i) => (
              <text
                key={i}
                x={chart.x(ts)}
                y={HEIGHT - 4}
                textAnchor={i === 0 ? 'start' : i === 2 ? 'end' : 'middle'}
                className="fill-tg-hint text-[10px]"
              >
                {fmtTime(ts)}
              </text>
            ))}
            <path d={chart.d} fill="none" strokeWidth={1.75} className="stroke-tg-link" />
            {shown ? (
              <>
                <line
                  x1={chart.x(shown.t)}
                  x2={chart.x(shown.t)}
                  y1={PAD.top}
                  y2={HEIGHT - PAD.bottom}
                  className="stroke-tg-hint/50"
                />
                <circle cx={chart.x(shown.t)} cy={chart.y(shown.p)} r={3.5} className="fill-tg-link" />
              </>
            ) : (
              <circle
                cx={chart.x(points[points.length - 1].t)}
                cy={chart.y(points[points.length - 1].p)}
                r={3.5}
                className="fill-tg-link"
              />
            )}
          </svg>
        )}
      </div>

      <div className="mt-1 flex justify-end gap-1">
        {INTERVALS.map((iv) => (
          <button
            key={iv}
            type="button"
            onClick={() => {
              setHover(null);
              setInterval(iv);
            }}
            className={clsx(
              'rounded-md px-2 py-0.5 text-[11px] font-semibold',
              iv === interval ? 'bg-tg-bg text-tg-text' : 'text-tg-hint',
            )}
          >
            {t(`chart.i.${iv}` as MessageKey)}
          </button>
        ))}
      </div>
    </section>
  );
}
