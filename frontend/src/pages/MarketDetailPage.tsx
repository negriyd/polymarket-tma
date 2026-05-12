import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { api } from '@/lib/api/endpoints';
import { Spinner } from '@/components/Spinner';
import { ErrorView } from '@/components/ErrorView';
import { subscribeToMarket } from '@/lib/ws/stompClient';
import { hapticImpact } from '@/lib/telegram/webApp';

export function MarketDetailPage() {
  const { conditionId = '' } = useParams();
  const qc = useQueryClient();
  const [livePayload, setLivePayload] = useState<unknown>(null);

  const market = useQuery({
    queryKey: ['market', conditionId],
    queryFn: () => api.getMarket(conditionId),
    enabled: !!conditionId,
  });

  const favorites = useQuery({
    queryKey: ['favorites'],
    queryFn: api.listFavorites,
  });
  const isFavorite = favorites.data?.some((f) => f.conditionId === conditionId) ?? false;

  const toggleFav = useMutation({
    mutationFn: () =>
      isFavorite ? api.removeFavorite(conditionId) : api.addFavorite(conditionId).then(() => undefined),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['favorites'] }),
  });

  useEffect(() => {
    if (!conditionId) return;
    let cleanup: (() => void) | null = null;
    subscribeToMarket(conditionId, (msg) => setLivePayload(msg))
      .then((c) => {
        cleanup = c;
      })
      .catch(() => {
        /* WS unavailable - fall back to polling via REST query */
      });
    return () => {
      cleanup?.();
    };
  }, [conditionId]);

  if (market.isLoading) return <Spinner label="Loading market…" />;
  if (market.error || !market.data)
    return <ErrorView message="Failed to load market" onRetry={() => market.refetch()} />;

  const m = market.data;
  const priceAt = (idx: number): number | undefined => {
    const raw = m.outcomePrices?.[idx];
    return raw == null ? undefined : Number(raw);
  };
  const yesIdx = m.outcomes?.findIndex((o) => o.toLowerCase() === 'yes') ?? -1;
  const noIdx = m.outcomes?.findIndex((o) => o.toLowerCase() === 'no') ?? -1;
  const yesPrice = priceAt(yesIdx >= 0 ? yesIdx : 0);
  const noPrice = priceAt(noIdx >= 0 ? noIdx : 1);

  return (
    <div className="space-y-4">
      <Link to="/markets" className="inline-flex items-center text-sm text-tg-hint">
        ← Back
      </Link>

      <header className="space-y-2">
        <div className="flex items-start gap-3">
          {m.image && (
            <img src={m.image} alt="" className="h-16 w-16 shrink-0 rounded-lg object-cover" />
          )}
          <div className="min-w-0 flex-1">
            <h1 className="text-xl font-semibold leading-snug">{m.question}</h1>
            {m.category && <p className="mt-1 text-xs uppercase tracking-wide text-tg-hint">{m.category}</p>}
          </div>
          <button
            onClick={() => {
              hapticImpact('medium');
              toggleFav.mutate();
            }}
            className="text-2xl"
            aria-label={isFavorite ? 'Unsave' : 'Save'}
            disabled={toggleFav.isPending}
          >
            {isFavorite ? '★' : '☆'}
          </button>
        </div>
      </header>

      <section className="grid grid-cols-2 gap-2">
        <PriceTile label="YES" price={yesPrice} accent="emerald" />
        <PriceTile label="NO" price={noPrice} accent="rose" />
      </section>

      <section className="grid grid-cols-3 gap-2">
        <Stat label="Vol 24h" value={fmt(m.volume24h)} />
        <Stat label="Liquidity" value={fmt(m.liquidity)} />
        <Stat label="Ends" value={m.endDate ? new Date(m.endDate).toLocaleDateString() : '—'} />
      </section>

      {m.description && (
        <section>
          <h2 className="mb-1 text-sm font-semibold text-tg-text">About</h2>
          <p className="whitespace-pre-line text-sm text-tg-hint">{m.description}</p>
        </section>
      )}

      <section className="rounded-xl bg-tg-secondary p-3 text-xs text-tg-hint">
        <p className="mb-1 font-medium text-tg-text">Live stream</p>
        {livePayload ? (
          <pre className="max-h-32 overflow-auto text-[10px] leading-tight">
            {JSON.stringify(livePayload, null, 2)}
          </pre>
        ) : (
          <p>Waiting for updates…</p>
        )}
      </section>
    </div>
  );
}

function PriceTile({ label, price, accent }: { label: string; price?: number; accent: 'emerald' | 'rose' }) {
  const pct = price == null ? null : Math.round(price * 100);
  const accentClass = accent === 'emerald' ? 'bg-emerald-500/15 text-emerald-500' : 'bg-rose-500/15 text-rose-500';
  return (
    <div className={`rounded-xl px-3 py-3 text-center ${accentClass}`}>
      <p className="text-xs font-semibold uppercase tracking-wide">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums">{pct == null ? '—' : `${pct}%`}</p>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-tg-secondary px-3 py-2">
      <p className="text-[10px] uppercase tracking-wide text-tg-hint">{label}</p>
      <p className="mt-0.5 text-sm font-semibold text-tg-text">{value}</p>
    </div>
  );
}

function fmt(value?: number): string {
  if (value == null) return '—';
  if (value >= 1_000_000) return `$${(value / 1_000_000).toFixed(2)}M`;
  if (value >= 1_000) return `$${(value / 1_000).toFixed(1)}K`;
  return `$${value.toFixed(0)}`;
}
