import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { api } from '@/lib/api/endpoints';
import { Spinner } from '@/components/Spinner';
import { ErrorView } from '@/components/ErrorView';
import { subscribeToMarket } from '@/lib/ws/stompClient';
import { hapticImpact } from '@/lib/telegram/webApp';
import { isPriceChangeMessage } from '@/lib/ws/livePayload';
import { isWalletConfigured } from '@/features/wallet/useWallet';
import { MarketTradeSheet } from '@/features/trading/MarketTradeSheet';
import { ClobTradeSheet } from '@/features/trading/ClobTradeSheet';
import { outcomeText, Trans, useLocale, useT } from '@/lib/i18n';

export function MarketDetailPage() {
  const { marketId = '' } = useParams();
  const t = useT();
  const locale = useLocale();
  const qc = useQueryClient();
  const [liveEnabled, setLiveEnabled] = useState(false);
  const [commentsEnabled, setCommentsEnabled] = useState(false);
  const [livePayload, setLivePayload] = useState<unknown>(null);
  const [tradeOutcome, setTradeOutcome] = useState<'yes' | 'no' | null>(null);
  const [privyTradeHint, setPrivyTradeHint] = useState(false);

  const market = useQuery({
    queryKey: ['market', marketId],
    queryFn: () => api.getMarket(marketId),
    enabled: !!marketId,
  });

  const comments = useQuery({
    queryKey: ['comments', marketId],
    queryFn: () => api.listMarketComments(marketId, 0, 30),
    enabled: !!marketId && commentsEnabled,
  });

  const favorites = useQuery({
    queryKey: ['favorites'],
    queryFn: api.listFavorites,
  });

  const betsCfg = useQuery({
    queryKey: ['bets-config'],
    queryFn: api.getBetsConfig,
    staleTime: 5 * 60_000,
  });

  const cid = market.data?.conditionId;

  const betPool = useQuery({
    queryKey: ['bet-pool', cid ?? ''],
    queryFn: () => api.getBetPool(cid!),
    enabled: !!cid && betsCfg.data?.enabled === true,
    staleTime: 15_000,
  });

  const isFavorite =
    cid != null && (favorites.data?.some((f) => f.conditionId === cid) ?? false);

  const toggleFav = useMutation({
    mutationFn: () => {
      if (!cid) return Promise.resolve();
      return isFavorite ? api.removeFavorite(cid) : api.addFavorite(cid).then(() => undefined);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['favorites'] }),
  });

  useEffect(() => {
    if (!cid || !liveEnabled) {
      setLivePayload(null);
      return;
    }
    let cleanup: (() => void) | null = null;
    subscribeToMarket(cid.toLowerCase(), (msg) => setLivePayload(msg))
      .then((c) => {
        cleanup = c;
      })
      .catch(() => {
        /* WS unavailable */
      });
    return () => {
      cleanup?.();
    };
  }, [cid, liveEnabled]);

  if (market.isLoading) return <Spinner label={t('market.loading')} />;
  if (market.error || !market.data)
    return <ErrorView message={t('market.loadFailed')} onRetry={() => market.refetch()} />;

  const m = market.data;
  const priceAt = (idx: number): number | undefined => {
    const raw = m.outcomePrices?.[idx];
    return raw == null ? undefined : Number(raw);
  };
  const yesIdx = m.outcomes?.findIndex((o) => o.toLowerCase() === 'yes') ?? -1;
  const noIdx = m.outcomes?.findIndex((o) => o.toLowerCase() === 'no') ?? -1;
  const yesPrice = priceAt(yesIdx >= 0 ? yesIdx : 0);
  const noPrice = priceAt(noIdx >= 0 ? noIdx : 1);
  const tokens = m.clobTokenIds ?? [];
  const yesTok = yesIdx >= 0 ? tokens[yesIdx] : tokens[0];
  const noTok = noIdx >= 0 ? tokens[noIdx] : tokens[1];
  const canTradeYes = betsCfg.data?.enabled === true || !!yesTok;
  const canTradeNo = betsCfg.data?.enabled === true || !!noTok;

  const openTrade = (side: 'yes' | 'no') => {
    hapticImpact('light');
    if (!isWalletConfigured()) {
      setPrivyTradeHint(true);
      return;
    }
    const ok = side === 'yes' ? canTradeYes : canTradeNo;
    if (!ok) return;
    setTradeOutcome(side);
  };

  const outcomeLabel = (assetId: string) => {
    const tokens = m.clobTokenIds ?? [];
    const outcomes = m.outcomes ?? [];
    const idx = tokens.findIndex((t) => t === assetId);
    if (idx >= 0 && outcomes[idx]) return outcomeText(outcomes[idx], t);
    const short = assetId.length > 12 ? `${assetId.slice(0, 6)}…${assetId.slice(-4)}` : assetId;
    return t('market.outcomeShort', { id: short });
  };

  return (
    <div className="max-w-full min-w-0 space-y-4">
      <Link to="/markets" className="inline-flex items-center text-sm text-tg-hint">
        {t('common.back')}
      </Link>

      <header className="space-y-2">
        <div className="flex items-start gap-3">
          {m.image && (
            <img src={m.image} alt="" className="h-16 w-16 shrink-0 rounded-lg object-cover" />
          )}
          <div className="min-w-0 flex-1">
            <h1 className="break-words text-xl font-semibold leading-snug">{m.question}</h1>
            {m.category && <p className="mt-1 text-xs uppercase tracking-wide text-tg-hint">{m.category}</p>}
          </div>
          <button
            onClick={() => {
              hapticImpact('medium');
              toggleFav.mutate();
            }}
            className="shrink-0 text-2xl"
            aria-label={isFavorite ? t('market.unsave') : t('market.save')}
            disabled={!cid || toggleFav.isPending}
          >
            {isFavorite ? '★' : '☆'}
          </button>
        </div>
      </header>

      {privyTradeHint && (
        <div className="rounded-xl bg-tg-secondary p-3 text-sm text-tg-hint ring-1 ring-tg-hint/20">
          <Trans
            text={t('market.privyHint')}
            slots={{
              env: <code className="text-tg-text">VITE_PRIVY_APP_ID</code>,
              wallet: (
                <Link to="/wallet" className="text-tg-button underline">
                  {t('nav.wallet')}
                </Link>
              ),
            }}
          />
          <button
            type="button"
            className="ml-2 text-xs underline"
            onClick={() => setPrivyTradeHint(false)}
          >
            {t('common.dismiss')}
          </button>
        </div>
      )}

      <section className="grid grid-cols-2 gap-2">
        <PriceTile
          label={t('common.yes')}
          price={yesPrice}
          accent="emerald"
          disabled={!canTradeYes}
          onPress={() => openTrade('yes')}
          actionLabel={t(betsCfg.data?.enabled ? 'market.bet' : 'market.buy', { outcome: t('common.yes') })}
        />
        <PriceTile
          label={t('common.no')}
          price={noPrice}
          accent="rose"
          disabled={!canTradeNo}
          onPress={() => openTrade('no')}
          actionLabel={t(betsCfg.data?.enabled ? 'market.bet' : 'market.buy', { outcome: t('common.no') })}
        />
      </section>

      {betsCfg.data?.enabled && betPool.data && (
        <section className="rounded-xl bg-tg-secondary p-3">
          <h2 className="text-sm font-semibold text-tg-text">{t('market.pool')}</h2>
          <div className="mt-2 grid grid-cols-2 gap-2 text-xs">
            {betPool.data.sides.map((s) => (
              <div key={s.outcomeIndex} className="rounded-lg bg-tg-bg/40 px-2 py-2">
                <p className="font-medium text-tg-text">{outcomeText(s.outcomeLabel, t)}</p>
                <p className="tabular-nums text-tg-hint">${s.totalUsdc}</p>
                <p className="text-[10px] text-tg-hint">
                  {t('market.implied', { pct: (Number(s.impliedProbability) * 100).toFixed(1) })}
                </p>
              </div>
            ))}
          </div>
          <p className="mt-2 text-[10px] text-tg-hint">
            {t('market.poolTotal', {
              total: betPool.data.totalPoolUsdc,
              fee: (betPool.data.platformFeeBps / 100).toFixed(2),
            })}
          </p>
        </section>
      )}

      {isWalletConfigured() && tradeOutcome != null &&
        (betsCfg.data?.enabled ? (
          <MarketTradeSheet
            market={m}
            outcome={tradeOutcome}
            open
            onClose={() => setTradeOutcome(null)}
          />
        ) : (
          <ClobTradeSheet
            market={m}
            outcome={tradeOutcome}
            open
            onClose={() => setTradeOutcome(null)}
          />
        ))}

      <section className="grid grid-cols-3 gap-2">
        <Stat label={t('market.vol24h')} value={fmt(m.volume24hr ?? m.volume24h)} />
        <Stat label={t('market.liquidity')} value={fmt(m.liquidity)} />
        <Stat label={t('market.ends')} value={m.endDate ? new Date(m.endDate).toLocaleDateString(locale) : '—'} />
      </section>

      {m.description && (
        <section>
          <h2 className="mb-1 text-sm font-semibold text-tg-text">{t('market.about')}</h2>
          <p className="whitespace-pre-line text-sm text-tg-hint">{m.description}</p>
        </section>
      )}

      <section className="rounded-xl bg-tg-secondary p-3">
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-sm font-semibold text-tg-text">{t('market.liveTrades')}</h2>
          <button
            type="button"
            role="switch"
            aria-checked={liveEnabled}
            onClick={() => setLiveEnabled((x) => !x)}
            className={
              liveEnabled
                ? 'rounded-full bg-tg-button px-3 py-1 text-xs font-medium text-tg-buttonText'
                : 'rounded-full bg-tg-secondary px-3 py-1 text-xs font-medium text-tg-hint ring-1 ring-tg-hint/30'
            }
          >
            {liveEnabled ? t('common.on') : t('common.off')}
          </button>
        </div>
        {!liveEnabled ? (
          <p className="mt-2 text-xs text-tg-hint">
            {t('market.liveOff')}
          </p>
        ) : isPriceChangeMessage(livePayload) ? (
          <LiveTradesList
            message={livePayload}
            outcomeLabel={outcomeLabel}
            ts={livePayload.timestamp}
          />
        ) : livePayload != null ? (
          <p className="mt-2 text-xs text-tg-hint">{t('market.liveUnknown')}</p>
        ) : (
          <p className="mt-2 text-xs text-tg-hint">{t('market.liveConnecting')}</p>
        )}
      </section>

      <section className="rounded-xl bg-tg-secondary p-3">
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-sm font-semibold text-tg-text">{t('market.comments')}</h2>
          <button
            type="button"
            role="switch"
            aria-checked={commentsEnabled}
            onClick={() =>
              setCommentsEnabled((x) => {
                const next = !x;
                if (next) hapticImpact('light');
                return next;
              })
            }
            className={
              commentsEnabled
                ? 'rounded-full bg-tg-button px-3 py-1 text-xs font-medium text-tg-buttonText'
                : 'rounded-full bg-tg-secondary px-3 py-1 text-xs font-medium text-tg-hint ring-1 ring-tg-hint/30'
            }
          >
            {commentsEnabled ? t('common.on') : t('common.off')}
          </button>
        </div>
        {!commentsEnabled ? (
          <p className="mt-2 text-xs text-tg-hint">{t('market.commentsOff')}</p>
        ) : (
          <>
            {comments.isLoading && <p className="mt-2 text-xs text-tg-hint">{t('market.commentsLoading')}</p>}
            {comments.error && (
              <button
                type="button"
                className="mt-2 text-xs text-tg-button underline"
                onClick={() => comments.refetch()}
              >
                {t('market.commentsFailed')}
              </button>
            )}
            {comments.data && comments.data.length === 0 && !comments.isLoading && (
              <p className="mt-2 text-xs text-tg-hint">{t('market.commentsEmpty')}</p>
            )}
            <ul className="mt-1 space-y-3">
              {comments.data?.map((c) => (
                <li key={c.id} className="border-t border-tg-hint/10 pt-3 first:border-t-0 first:pt-0">
                  <div className="flex gap-2">
                    {c.authorAvatar ? (
                      <img
                        src={c.authorAvatar}
                        alt=""
                        className="h-8 w-8 shrink-0 rounded-full object-cover"
                      />
                    ) : (
                      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-tg-hint/20 text-xs font-semibold text-tg-hint">
                        {(c.author || '?').slice(0, 1).toUpperCase()}
                      </div>
                    )}
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0">
                        <span className="text-xs font-semibold text-tg-text">{c.author}</span>
                        {c.createdAt && (
                          <span className="text-[10px] text-tg-hint">
                            {new Date(c.createdAt).toLocaleString(locale)}
                          </span>
                        )}
                      </div>
                      <p className="mt-0.5 whitespace-pre-wrap text-sm text-tg-text">{c.body}</p>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          </>
        )}
      </section>
    </div>
  );
}

function LiveTradesList({
  message,
  outcomeLabel,
  ts,
}: {
  message: import('@/lib/ws/livePayload').PriceChangeMessage;
  outcomeLabel: (assetId: string) => string;
  ts?: string;
}) {
  const t = useT();
  const locale = useLocale();
  const timeLabel =
    ts != null && ts !== ''
      ? (() => {
          const n = Number(ts);
          if (!Number.isFinite(n)) return null;
          const ms = n > 1e12 ? n : n * 1000;
          return new Date(ms).toLocaleTimeString(locale);
        })()
      : null;

  return (
    <div className="mt-2 space-y-2">
      {timeLabel && <p className="text-[10px] uppercase tracking-wide text-tg-hint">{t('market.updated', { time: timeLabel })}</p>}
      <ul className="space-y-2">
        {message.price_changes?.map((ch, i) => {
          const isBuy = ch.side?.toUpperCase() === 'BUY';
          const pct = (Number(ch.price) * 100).toFixed(1);
          const bid = ch.best_bid != null ? (Number(ch.best_bid) * 100).toFixed(1) : null;
          const ask = ch.best_ask != null ? (Number(ch.best_ask) * 100).toFixed(1) : null;
          return (
            <li
              key={ch.hash ?? `${ch.asset_id}-${i}`}
              className="rounded-lg bg-tg-bg/40 px-2 py-2 text-xs text-tg-text ring-1 ring-tg-hint/10"
            >
              <span className="font-medium text-tg-text">{outcomeLabel(ch.asset_id)}</span>
              <span className="text-tg-hint"> · </span>
              <span className={isBuy ? 'text-emerald-500' : 'text-rose-500'}>
                {isBuy ? t('market.side.buy') : t('market.side.sell')}
              </span>
              <span className="text-tg-hint"> · </span>
              <span className="tabular-nums">{pct}%</span>
              <span className="text-tg-hint">{t('market.atSize')}</span>
              <span className="tabular-nums">{ch.size}</span>
              {(bid != null || ask != null) && (
                <span className="mt-1 block text-[10px] text-tg-hint">
                  {t('market.book')} {bid != null ? t('market.bid', { pct: bid }) : ''}
                  {bid != null && ask != null ? ' · ' : ''}
                  {ask != null ? t('market.ask', { pct: ask }) : ''}
                </span>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function PriceTile({
  label,
  price,
  accent,
  onPress,
  disabled,
  actionLabel,
}: {
  label: string;
  price?: number;
  accent: 'emerald' | 'rose';
  onPress?: () => void;
  disabled?: boolean;
  actionLabel?: string;
}) {
  const t = useT();
  const pct = price == null ? null : Math.round(price * 100);
  const accentClass = accent === 'emerald' ? 'bg-emerald-500/15 text-emerald-500' : 'bg-rose-500/15 text-rose-500';
  const className = `rounded-xl px-3 py-3 text-center ${accentClass} ${
    onPress && !disabled ? 'cursor-pointer transition active:scale-[0.98]' : ''
  } ${disabled ? 'opacity-50' : ''}`;
  const cta = actionLabel ?? t('market.buy', { outcome: label });
  return (
    <button
      type="button"
      className={className}
      onClick={onPress}
      disabled={disabled || !onPress}
      aria-label={disabled || !onPress ? t('market.priceLabel', { outcome: label }) : cta}
    >
      <p className="text-xs font-semibold uppercase tracking-wide">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums">{pct == null ? '—' : `${pct}%`}</p>
      {onPress && !disabled && (
        <p className="mt-1 text-[10px] font-medium uppercase tracking-wide opacity-80">{t('market.tapTo', { action: cta.toLowerCase() })}</p>
      )}
    </button>
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
