import { useCallback, useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useSignTypedData, useWallets, type SignTypedDataParams } from '@privy-io/react-auth';
import { formatUnits } from 'viem';
import { api } from '@/lib/api/endpoints';
import type { Market } from '@/lib/api/types';
import { formatError as formatErr } from '@/lib/errors';
import { Trans, useT, type MessageKey } from '@/lib/i18n';
import { RiskNote } from '@/components/RiskNote';
import { useWallet } from '@/features/wallet/useWallet';
import { placeClobOrder } from './placeOrder';
import { hapticImpact } from '@/lib/telegram/webApp';
import { ShareButton } from '@/features/share/ShareButton';
import { markFirstBetDone } from '@/features/onboarding/firstBet';
import { NotifyPrompt } from '@/features/predictions/NotifyPrompt';
import { INSTANT_AMOUNTS, INSTANT_MIN, bestAsk, capPrice, walkAsks } from './instantBuy';

type OutcomeSide = 'yes' | 'no';

/** CLOB order statuses with their own wording; anything else is shown as-is. */
const STATUS_KEYS = new Set(['matched', 'live', 'delayed', 'unmatched']);

function pickTokenIds(market: Market): { yesToken?: string; noToken?: string } {
  const tokens = market.clobTokenIds ?? [];
  const outcomes = market.outcomes ?? [];
  const yesIdx = outcomes.findIndex((o) => o.toLowerCase() === 'yes');
  const noIdx = outcomes.findIndex((o) => o.toLowerCase() === 'no');
  return {
    yesToken: yesIdx >= 0 ? tokens[yesIdx] : tokens[0],
    noToken: noIdx >= 0 ? tokens[noIdx] : tokens[1],
  };
}

function pickPrices(market: Market): { yes?: number; no?: number } {
  const priceAt = (idx: number): number | undefined => {
    const raw = market.outcomePrices?.[idx];
    return raw == null ? undefined : Number(raw);
  };
  const yesIdx = market.outcomes?.findIndex((o) => o.toLowerCase() === 'yes') ?? -1;
  const noIdx = market.outcomes?.findIndex((o) => o.toLowerCase() === 'no') ?? -1;
  return {
    yes: priceAt(yesIdx >= 0 ? yesIdx : 0),
    no: priceAt(noIdx >= 0 ? noIdx : 1),
  };
}


interface Props {
  market: Market;
  outcome: OutcomeSide;
  open: boolean;
  onClose: () => void;
}

/**
 * Polymarket CLOB v2 limit order: prepare → Privy signs the ERC-7739 wrapper → submit.
 * Orders are {@code POLY_1271}: maker = the user's deposit wallet (pUSD), signer key = Privy EOA.
 */
export function ClobTradeSheet({ market, outcome, open, onClose }: Props) {
  const qc = useQueryClient();
  const t = useT();
  const w = useWallet();
  const { signTypedData } = useSignTypedData();
  const { wallets } = useWallets();

  const activeWallet = wallets.find(
    (wallet) => w.address && wallet.address.toLowerCase() === w.address.toLowerCase(),
  );
  const walletChainId = (() => {
    const cid = activeWallet?.chainId;
    if (!cid) return undefined;
    if (typeof cid === 'string') {
      const m = cid.match(/^eip155:(\d+)$/);
      return m ? Number(m[1]) : Number(cid);
    }
    return Number(cid);
  })();

  const { yesToken, noToken } = pickTokenIds(market);
  const { yes: yesP, no: noP } = pickPrices(market);
  const tokenId = outcome === 'yes' ? yesToken : noToken;
  const refPrice = outcome === 'yes' ? yesP : noP;
  const cid = market.conditionId;

  const [priceStr, setPriceStr] = useState('');
  const tick = market.orderPriceMinTickSize ?? 0.01;
  const tickDecimals = Math.max(0, Math.round(-Math.log10(tick)));
  const toTick = useCallback((p: number) => p.toFixed(tickDecimals), [tickDecimals]);
  const minShares = market.orderMinSize ?? null;
  const [usdcStr, setUsdcStr] = useState('5');
  const [mode, setMode] = useState<'instant' | 'limit'>('instant');
  const instant = mode === 'instant';
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const feeCfg = useQuery({
    queryKey: ['fee-config'],
    queryFn: api.getFeeConfig,
    staleTime: 5 * 60_000,
  });

  const depositWallet = useQuery({
    queryKey: ['deposit-wallet', w.address],
    queryFn: api.getDepositWallet,
    enabled: open && !!w.address,
    staleTime: 15_000,
  });
  const ready = depositWallet.data?.readyToTrade === true;

  const book = useQuery({
    queryKey: ['orderbook', tokenId],
    queryFn: () => api.getOrderbook(cid!, tokenId!),
    enabled: open && instant && !!cid && !!tokenId,
    refetchInterval: 10_000,
  });
  const ask = useMemo(() => bestAsk(book.data?.asks), [book.data]);
  const cap = ask == null ? null : capPrice(ask, tick);
  const balance =
    depositWallet.data?.collateralBalance != null
      ? Number(formatUnits(BigInt(depositWallet.data.collateralBalance), 6))
      : null;

  useEffect(() => {
    if (!open) return;
    setError(null);
    setSuccess(null);
    // Round the reference price up to the tick: a BUY a cent above mid usually meets the ask.
    const start = refPrice != null ? Math.ceil(Number(refPrice) / tick - 1e-9) * tick : 0.5;
    setPriceStr(toTick(Math.min(1 - tick, Math.max(tick, start))));
  }, [open, outcome, refPrice, market.conditionId, tick, toTick]);

  const priceNum = Number(priceStr);
  const usdcNum = Number(usdcStr);
  const onTick = Math.abs(priceNum / tick - Math.round(priceNum / tick)) < 1e-6;
  const priceOk = Number.isFinite(priceNum) && priceNum > 0 && priceNum < 1 && onTick;
  const usdcOk = Number.isFinite(usdcNum) && usdcNum > 0;
  // CLOB takes sizes in whole cents of a share; cut down so the order never exceeds the spend.
  const shares = priceOk && usdcOk ? Math.floor((usdcNum / priceNum) * 100 + 1e-9) / 100 : null;
  const tooSmall = shares != null && minShares != null && shares < minShares;
  const minSpend = minShares != null && priceOk ? Math.ceil(minShares * priceNum * 100) / 100 : null;
  const fill = cap != null && usdcOk ? walkAsks(book.data?.asks, usdcNum, cap) : null;
  const instantOk = cap != null && usdcOk && usdcNum >= INSTANT_MIN;
  const canSubmit =
    w.ready && w.authenticated && !!w.address && ready && !!tokenId && !!cid &&
    (instant ? instantOk : shares != null && shares > 0 && !tooSmall);

  const trade = useMutation({
    mutationFn: async () => {
      setError(null);
      setSuccess(null);
      if (!cid || !tokenId) throw new Error(t('trade.missingToken'));
      if (!w.address) throw new Error(t('common.walletNotConnected'));
      if (instant ? !instantOk : shares == null || shares <= 0) throw new Error(t('trade.invalid'));

      const address = w.address;
      const sign = async (td: unknown) =>
        (await signTypedData(td as SignTypedDataParams, { address })).signature;
      return placeClobOrder({
        address,
        conditionId: cid,
        tokenId,
        side: 'BUY',
        price: instant ? cap! : priceNum,
        size: instant ? Math.floor((usdcNum / cap!) * 100) / 100 : shares!,
        instant: instant ? { amount: Math.floor(usdcNum * 100) / 100 } : undefined,
        sign,
        ensurePolygon: async () => {
          if (activeWallet && 'switchChain' in activeWallet && walletChainId !== 137) {
            try {
              await (activeWallet as unknown as { switchChain: (id: number) => Promise<void> }).switchChain(137);
            } catch (err) {
              console.warn('Privy wallet.switchChain(137) failed:', err);
            }
          }
        },
        onFeeError: (err) => setError(t('trade.feeFailed', { error: formatErr(err) })),
      });
    },
    onSuccess: (sub) => {
      markFirstBetDone();
      const key = `trade.status.${sub.status}` as MessageKey;
      const spent = sub.filledUsdc != null ? Number(sub.filledUsdc) : NaN;
      const text =
        instant && sub.status === 'matched' && Number.isFinite(spent) && spent > 0
          ? t('trade.bought', { amount: spent.toFixed(2) })
          : STATUS_KEYS.has(sub.status)
            ? t(key)
            : t('trade.statusOther', { status: sub.status });
      setSuccess(`${text}${sub.orderId ? ` · ${sub.orderId.slice(0, 10)}…` : ''}`);
      hapticImpact('medium');
      qc.invalidateQueries({ queryKey: ['positions'] });
      qc.invalidateQueries({ queryKey: ['deposit-wallet'] });
    },
    onError: (e) => {
      setError(formatErr(e));
      hapticImpact('light');
    },
  });

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/50" role="dialog" aria-modal="true">
      <button type="button" className="absolute inset-0 cursor-default" aria-label={t('common.close')} onClick={onClose} />
      <div className="relative w-full max-w-lg rounded-t-2xl bg-tg-bg p-4 shadow-lg ring-1 ring-tg-hint/10">
        <div className="mb-3 flex items-center justify-between gap-2">
          <h2 className="min-w-0 flex-1 text-lg font-semibold leading-snug text-tg-text">
            {t('trade.title', {
              outcome: t(outcome === 'yes' ? 'common.yes' : 'common.no'),
              question: market.question.length > 48 ? `${market.question.slice(0, 48)}…` : market.question,
            })}
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="shrink-0 rounded-lg px-2 py-1 text-sm text-tg-hint hover:bg-tg-secondary"
          >
            ✕
          </button>
        </div>

        {!w.authenticated ? (
          <p className="mb-3 rounded-lg bg-tg-secondary p-3 text-sm text-tg-hint">
            <Trans
              text={t('trade.connectFirst')}
              slots={{
                wallet: (
                  <Link to="/wallet" className="font-medium text-tg-button underline" onClick={onClose}>
                    {t('nav.wallet')}
                  </Link>
                ),
              }}
            />
          </p>
        ) : (
          depositWallet.data &&
          !ready && (
            <p className="mb-3 rounded-lg bg-tg-secondary p-3 text-sm text-tg-hint">
              <Trans
                text={t('trade.activateFirst')}
                slots={{
                  wallet: (
                    <Link to="/wallet" className="font-medium text-tg-button underline" onClick={onClose}>
                      {t('nav.wallet')}
                    </Link>
                  ),
                }}
              />
            </p>
          )
        )}

        {!tokenId && (
          <p className="mb-3 text-sm text-rose-500">{t('trade.noToken')}</p>
        )}

        <div className="mb-3 grid grid-cols-2 gap-1 rounded-xl bg-tg-secondary p-1 text-sm">
          {(['instant', 'limit'] as const).map((m) => (
            <button
              key={m}
              type="button"
              onClick={() => {
                setMode(m);
                setError(null);
                setSuccess(null);
              }}
              className={`rounded-lg py-1.5 font-medium ${mode === m ? 'bg-tg-bg text-tg-text shadow-sm' : 'text-tg-hint'}`}
            >
              {t(m === 'instant' ? 'trade.mode.instant' : 'trade.mode.limit')}
            </button>
          ))}
        </div>

        <div className="space-y-3">
          {instant ? (
            <div>
              <span className="text-xs font-medium uppercase tracking-wide text-tg-hint">{t('trade.amount')}</span>
              <div className="mt-1 flex gap-2">
                {INSTANT_AMOUNTS.map((a) => (
                  <button
                    key={a}
                    type="button"
                    onClick={() => setUsdcStr(String(a))}
                    className={`flex-1 rounded-xl py-2 text-base font-semibold ${
                      usdcNum === a ? 'bg-tg-button text-tg-buttonText' : 'bg-tg-secondary text-tg-text'
                    }`}
                  >
                    ${a}
                  </button>
                ))}
                <input
                  type="number"
                  inputMode="decimal"
                  min={INSTANT_MIN}
                  step="1"
                  value={usdcStr}
                  onChange={(e) => setUsdcStr(e.target.value)}
                  aria-label={t('trade.amount')}
                  className="w-20 rounded-xl border border-tg-hint/20 bg-tg-secondary px-2 py-2 text-center text-tg-text"
                />
              </div>
              {book.isFetched && cap == null ? (
                <p className="mt-2 text-xs text-amber-500">{t('trade.noAsks')}</p>
              ) : (
                cap != null &&
                fill != null && (
                  <p className="mt-2 text-sm text-tg-text">
                    {t('trade.instantSummary', { shares: fill.shares.toFixed(2), cents: Math.round(cap * 100) })}
                  </p>
                )
              )}
              {usdcOk && usdcNum < INSTANT_MIN && (
                <p className="mt-0.5 text-xs text-amber-500">{t('trade.instantMin', { min: INSTANT_MIN })}</p>
              )}
              {balance != null && (
                <p className="mt-0.5 text-[10px] text-tg-hint">
                  {t('trade.available', { balance: balance.toFixed(2) })}
                  {usdcOk && usdcNum > balance ? t('trade.exceedsBalance') : ''}
                </p>
              )}
            </div>
          ) : (
          <>
          <label className="block">
            <span className="text-xs font-medium uppercase tracking-wide text-tg-hint">
              {t('trade.limitPrice')}
            </span>
            <input
              type="number"
              inputMode="decimal"
              step={tick}
              min={tick}
              max={1 - tick}
              value={priceStr}
              onChange={(e) => setPriceStr(e.target.value)}
              className="mt-1 w-full rounded-xl border border-tg-hint/20 bg-tg-secondary px-3 py-2 text-tg-text"
            />
            <span className="mt-0.5 block text-[10px] text-tg-hint">
              {!onTick && priceStr
                ? t('trade.useSteps', { tick: toTick(tick) })
                : refPrice != null
                  ? t('trade.marketPrice', { cents: Math.round(refPrice * 100) })
                  : t('trade.enterPrice')}
            </span>
          </label>

          <label className="block">
            <span className="text-xs font-medium uppercase tracking-wide text-tg-hint">{t('trade.spend')}</span>
            <input
              type="number"
              inputMode="decimal"
              step="1"
              min={1}
              value={usdcStr}
              onChange={(e) => setUsdcStr(e.target.value)}
              className="mt-1 w-full rounded-xl border border-tg-hint/20 bg-tg-secondary px-3 py-2 text-tg-text"
            />
            {shares != null && priceOk && (
              <p className="mt-1 text-xs text-tg-hint">
                {t('trade.shares', { shares: shares.toFixed(2) })}
                {balance != null && usdcNum > balance ? t('trade.exceedsBalance') : ''}
              </p>
            )}
            {tooSmall && minSpend != null && (
              <p className="mt-0.5 text-xs text-amber-500">
                {t('trade.minOrder', { min: minShares ?? '', spend: minSpend.toFixed(2) })}
              </p>
            )}
            {balance != null && (
              <p className="mt-0.5 text-[10px] text-tg-hint">{t('trade.available', { balance: balance.toFixed(2) })}</p>
            )}
          </label>
          </>
          )}

          {feeCfg.data?.enabled && (instant || priceOk) && usdcOk && (
            <p className="rounded-lg bg-tg-secondary px-3 py-2 text-xs text-tg-hint">
              {t('trade.fee', {
                pct: (feeCfg.data.spreadBps / 100).toFixed(2),
                amount: ((usdcNum * feeCfg.data.spreadBps) / 10_000).toFixed(4),
              })}
              <span className="ml-1 text-tg-hint/70">{t('trade.feeNote')}</span>
            </p>
          )}
          {walletChainId !== undefined && walletChainId !== 137 && (
            <p className="text-xs text-amber-500">
              {t('trade.wrongChain', { chain: walletChainId })}
            </p>
          )}
          {error && <p className="text-sm text-rose-500">{error}</p>}
          {success && <p className="text-sm text-emerald-500">{success}</p>}
          {success && (
            <div className="space-y-2 rounded-xl bg-tg-button/10 p-3">
              <p className="text-sm text-tg-text">💬 {t('trade.shareHint')}</p>
              <ShareButton marketKey={market.id ?? market.conditionId} side={outcome} />
            </div>
          )}
          {success && <NotifyPrompt />}

          <p className="text-[10px] leading-snug text-tg-hint">
            {instant && cap != null ? t('trade.instantNote', { cents: Math.round(cap * 100) }) : t('trade.note')}
          </p>
          <RiskNote short />

          <button
            type="button"
            disabled={!canSubmit || trade.isPending}
            onClick={() => trade.mutate()}
            className="w-full rounded-xl bg-tg-button py-3 text-base font-semibold text-tg-buttonText disabled:opacity-50"
          >
            {trade.isPending
              ? t('trade.submitting')
              : instant
                ? t('trade.buyFor', {
                    outcome: t(outcome === 'yes' ? 'common.yes' : 'common.no'),
                    amount: usdcOk ? String(Math.floor(usdcNum * 100) / 100) : '…',
                  })
                : t('trade.submit')}
          </button>
        </div>
      </div>
    </div>
  );
}
