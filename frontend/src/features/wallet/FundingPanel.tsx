import { useEffect, useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { formatUnits } from 'viem';
import { api } from '@/lib/api/endpoints';
import type { BridgeDeposit, FundingOption } from '@/lib/api/types';
import { useLocale, useT, type TFunction } from '@/lib/i18n';
import { hapticImpact, tg } from '@/lib/telegram/webApp';

const IN_FLIGHT = new Set(['DEPOSIT_DETECTED', 'PROCESSING', 'ORIGIN_TX_CONFIRMED', 'SUBMITTED']);

function statusLabel(status: string, t: TFunction): { text: string; cls: string } {
  if (status === 'COMPLETED') return { text: t('fund.credited'), cls: 'text-emerald-500' };
  if (status === 'FAILED') return { text: t('fund.failed'), cls: 'text-rose-500' };
  if (IN_FLIGHT.has(status)) return { text: t('fund.inProgress'), cls: 'text-amber-500' };
  return { text: status, cls: 'text-tg-hint' };
}

function formatAmount(d: BridgeDeposit, options: FundingOption[]): string {
  const opt = options.find(
    (o) => o.chainId === d.fromChainId && o.tokenAddress.toLowerCase() === d.fromTokenAddress.toLowerCase(),
  );
  if (!opt || opt.decimals == null) return '';
  try {
    return `${Number(formatUnits(BigInt(d.fromAmountBaseUnit), opt.decimals)).toFixed(2)} ${opt.symbol}`;
  } catch {
    return '';
  }
}

/**
 * Top-up via the Polymarket bridge: pick a network + stablecoin, send to the shown address from any
 * exchange or wallet, and the bridge credits pUSD to the deposit wallet (no gas, no wrapping).
 */
export function FundingPanel() {
  const qc = useQueryClient();
  const t = useT();
  const locale = useLocale();
  const [selected, setSelected] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const funding = useQuery({
    queryKey: ['funding'],
    queryFn: api.getFunding,
    staleTime: 10 * 60_000,
  });

  const deposits = useQuery({
    queryKey: ['bridge-deposits'],
    queryFn: api.listBridgeDeposits,
    refetchInterval: (q) => ((q.state.data ?? []).some((d) => IN_FLIGHT.has(d.status)) ? 10_000 : 30_000),
  });

  // A finished deposit changes the pUSD balance shown by DepositWalletSection.
  const completedCount = deposits.data?.filter((d) => d.status === 'COMPLETED').length ?? 0;
  useEffect(() => {
    if (completedCount > 0) qc.invalidateQueries({ queryKey: ['deposit-wallet'] });
  }, [completedCount, qc]);

  const options = useMemo(() => funding.data?.options ?? [], [funding.data]);
  const key = (o: FundingOption) => `${o.chainId}:${o.tokenAddress}`;
  const chosen = options.find((o) => key(o) === selected);

  const copy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1_500);
    } catch {
      /* clipboard may be unavailable inside some Telegram clients */
    }
  };

  if (funding.isLoading) return <p className="text-xs text-tg-hint">{t('fund.loading')}</p>;
  if (funding.isError || !funding.data) {
    return <p className="text-xs text-rose-500">{t('fund.unavailable')}</p>;
  }

  // Wallet in Telegram sends USDT on Solana and Tron too; the bridge takes both (not TON).
  const walletOptions = ['Solana', 'Tron']
    .map((chain) => options.find((o) => o.symbol === 'USDT' && o.chainName === chain))
    .filter((o): o is FundingOption => !!o);

  return (
    <div className="space-y-2">
      {walletOptions.length > 0 && (
        <section className="space-y-1.5 rounded-lg bg-tg-bg p-2.5">
          <p className="text-xs font-semibold text-tg-text">👛 {t('fund.tgWallet.title')}</p>
          <ol className="list-decimal space-y-0.5 pl-4 text-[11px] leading-snug text-tg-text">
            <li>{t('fund.tgWallet.step1')}</li>
            <li>{t('fund.tgWallet.step2')}</li>
            <li>{t('fund.tgWallet.step3')}</li>
            <li>{t('fund.tgWallet.step4')}</li>
          </ol>
          <div className="flex flex-wrap gap-1.5">
            {walletOptions.map((o) => (
              <button
                key={key(o)}
                type="button"
                onClick={() => {
                  setSelected(key(o));
                  void copy(o.depositAddress);
                }}
                className={`rounded-lg px-2.5 py-1.5 text-xs font-medium ${
                  key(o) === selected ? 'bg-tg-button text-tg-buttonText' : 'bg-tg-secondary text-tg-text'
                }`}
              >
                {t('fund.tgWallet.copy', { chain: o.chainName })}
              </button>
            ))}
            <button
              type="button"
              onClick={() => {
                hapticImpact('light');
                if (tg.initData) tg.openTelegramLink('https://t.me/wallet');
                else window.open('https://t.me/wallet', '_blank');
              }}
              className="rounded-lg bg-tg-secondary px-2.5 py-1.5 text-xs font-medium text-tg-link"
            >
              {t('fund.tgWallet.open')} ↗
            </button>
          </div>
          <p className="text-[10px] leading-snug text-amber-500">{t('fund.tgWallet.warn')}</p>
        </section>
      )}

      <p className="text-[11px] leading-snug text-amber-500">
        {t('fund.warning')}
      </p>

      <div className="flex flex-wrap gap-1.5">
        {options.map((o) => (
          <button
            key={key(o)}
            type="button"
            onClick={() => setSelected(key(o))}
            className={`rounded-lg px-2.5 py-1.5 text-xs ${
              key(o) === selected ? 'bg-tg-button text-tg-buttonText' : 'bg-tg-bg text-tg-text'
            }`}
          >
            {o.symbol} · {o.chainName}
          </button>
        ))}
      </div>

      {chosen && (
        <button type="button" onClick={() => void copy(chosen.depositAddress)} className="w-full rounded-lg bg-tg-bg p-2 text-left">
          <span className="block text-[10px] uppercase tracking-wide text-tg-hint">
            {t('fund.send', { symbol: chosen.symbol, chain: chosen.chainName })}
            {chosen.minUsd != null ? t('fund.min', { min: chosen.minUsd }) : ''} ·{' '}
            {copied ? t('common.copied') : t('common.tapToCopy')}
          </span>
          <span className="block break-all font-mono text-sm text-tg-text">{chosen.depositAddress}</span>
          <span className="mt-1 block break-all text-[10px] text-tg-hint">{t('fund.token', { address: chosen.tokenAddress })}</span>
        </button>
      )}

      {deposits.data && deposits.data.length > 0 && (
        <ul className="space-y-1">
          {deposits.data.slice(0, 5).map((d, i) => {
            const s = statusLabel(d.status, t);
            return (
              <li key={`${d.txHash ?? 'pending'}:${i}`} className="flex justify-between gap-2 text-xs">
                <span className="text-tg-text">
                  {formatAmount(d, options) || t('fund.deposit')}
                  {d.createdTimeMs ? (
                    <span className="ml-1 text-tg-hint">{new Date(d.createdTimeMs).toLocaleString(locale)}</span>
                  ) : null}
                </span>
                <span className={`shrink-0 font-medium ${s.cls}`}>{s.text}</span>
              </li>
            );
          })}
        </ul>
      )}

      <p className="text-[10px] leading-snug text-tg-hint">
        {t('fund.note')}
      </p>
    </div>
  );
}
