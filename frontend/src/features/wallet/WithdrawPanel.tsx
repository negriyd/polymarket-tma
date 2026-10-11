import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSignTypedData, type SignTypedDataParams } from '@privy-io/react-auth';
import { formatUnits, parseUnits } from 'viem';
import { api } from '@/lib/api/endpoints';
import type { WithdrawDestination, WithdrawPrepared } from '@/lib/api/types';
import { formatError as formatErr } from '@/lib/errors';
import { useT } from '@/lib/i18n';
import { signAndRelayBatch, waitForRelayerTx } from './relayerTx';

interface Props {
  walletAddress: string;
}


const key = (d: WithdrawDestination) => `${d.chainId}:${d.tokenAddress}`;

/**
 * Withdraw pUSD from the deposit wallet: pUSD on Polygon directly, or a stablecoin on another
 * network via the Polymarket bridge. Two steps — prepare shows the bridge's estimate, then the
 * owner signs the batch — because small bridged withdrawals can lose a large share to fill costs.
 */
export function WithdrawPanel({ walletAddress }: Props) {
  const qc = useQueryClient();
  const t = useT();
  const { signTypedData } = useSignTypedData();
  const [selected, setSelected] = useState<string | null>(null);
  const [recipient, setRecipient] = useState('');
  const [amountStr, setAmountStr] = useState('');
  const [prepared, setPrepared] = useState<WithdrawPrepared | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  const options = useQuery({
    queryKey: ['withdraw-options'],
    queryFn: api.getWithdrawOptions,
    staleTime: 60_000,
  });

  const dest = options.data?.destinations.find((d) => key(d) === selected);
  const balance =
    options.data?.collateralBalance != null ? formatUnits(BigInt(options.data.collateralBalance), 6) : null;

  const reset = () => {
    setPrepared(null);
    setError(null);
    setDone(null);
  };

  const prepare = useMutation({
    mutationFn: () => {
      if (!dest) throw new Error(t('wd.chooseDest'));
      const amount = parseUnits(amountStr.trim() || '0', 6);
      if (amount <= 0n) throw new Error(t('wd.enterAmount'));
      return api.prepareWithdraw({
        toChainId: dest.chainId,
        toTokenAddress: dest.tokenAddress,
        recipient: recipient.trim(),
        amountBaseUnit: amount.toString(),
      });
    },
    onMutate: reset,
    onSuccess: setPrepared,
    onError: (e) => setError(formatErr(e)),
  });

  const confirm = useMutation({
    mutationFn: async () => {
      if (!prepared) throw new Error(t('wd.nothing'));
      const tx = await signAndRelayBatch(prepared.batch, async (td) =>
        (await signTypedData(td as SignTypedDataParams, { address: walletAddress })).signature,
      );
      if (!tx) throw new Error(t('wd.emptyBatch'));
      return waitForRelayerTx(tx);
    },
    onSuccess: (tx) => {
      setPrepared(null);
      setAmountStr('');
      const ref = tx.transactionHash?.slice(0, 10) ?? tx.transactionId;
      setDone(t(prepared?.route === 'BRIDGE' ? 'wd.bridged' : 'wd.done', { tx: ref }));
      qc.invalidateQueries({ queryKey: ['deposit-wallet'] });
      qc.invalidateQueries({ queryKey: ['withdraw-options'] });
    },
    onError: (e) => setError(formatErr(e)),
  });

  if (options.isLoading) return <p className="text-xs text-tg-hint">{t('wd.loading')}</p>;
  if (options.isError || !options.data) {
    return <p className="text-xs text-rose-500">{t('wd.unavailable')}</p>;
  }

  const sentNum = Number(amountStr) || 0;
  const received =
    prepared?.estReceivedBaseUnit != null && dest?.decimals != null
      ? Number(formatUnits(BigInt(prepared.estReceivedBaseUnit), dest.decimals))
      : null;
  const expensive = received != null && sentNum > 0 && received < sentNum * 0.9;

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-1.5">
        {options.data.destinations.map((d) => (
          <button
            key={key(d)}
            type="button"
            onClick={() => {
              setSelected(key(d));
              reset();
            }}
            className={`rounded-lg px-2.5 py-1.5 text-xs ${
              key(d) === selected ? 'bg-tg-button text-tg-buttonText' : 'bg-tg-bg text-tg-text'
            }`}
          >
            {d.symbol} · {d.chainName}
          </button>
        ))}
      </div>

      {dest && (
        <>
          <input
            value={recipient}
            onChange={(e) => {
              setRecipient(e.target.value);
              reset();
            }}
            placeholder={t('wd.recipient', { chain: dest.chainName })}
            className="w-full rounded-xl border border-tg-hint/20 bg-tg-bg px-3 py-2 font-mono text-xs text-tg-text"
          />
          <div className="flex gap-2">
            <input
              type="number"
              inputMode="decimal"
              min={0}
              value={amountStr}
              onChange={(e) => {
                setAmountStr(e.target.value);
                reset();
              }}
              placeholder={dest.minUsd != null ? t('wd.amountMin', { min: dest.minUsd }) : t('wd.amount')}
              className="min-w-0 flex-1 rounded-xl border border-tg-hint/20 bg-tg-bg px-3 py-2 text-sm text-tg-text"
            />
            {balance != null && (
              <button
                type="button"
                onClick={() => {
                  setAmountStr(balance);
                  reset();
                }}
                className="rounded-xl bg-tg-bg px-3 text-xs text-tg-hint"
              >
                {t('wd.max')}
              </button>
            )}
          </div>
          {dest.route === 'DIRECT' && (
            <p className="text-[10px] leading-snug text-amber-500">
              {t('wd.directNote')}
            </p>
          )}

          {!prepared ? (
            <button
              type="button"
              disabled={prepare.isPending || !recipient.trim() || !amountStr}
              onClick={() => prepare.mutate()}
              className="w-full rounded-xl bg-tg-bg py-2 text-sm font-semibold text-tg-text disabled:opacity-50"
            >
              {prepare.isPending ? t('wd.checking') : t('wd.review')}
            </button>
          ) : (
            <div className="space-y-2 rounded-lg bg-tg-bg p-2 text-xs text-tg-text">
              <p>
                {t('wd.summary', {
                  amount: amountStr,
                  received: received != null ? `≈ ${received.toFixed(2)} ${dest.symbol}` : dest.symbol,
                  chain: dest.chainName,
                })}
                {prepared.estDurationMs
                  ? t('wd.duration', { seconds: Math.ceil(prepared.estDurationMs / 1000) })
                  : ''}
              </p>
              {expensive && (
                <p className="text-rose-500">
                  {t('wd.expensive', { pct: (100 - (received! / sentNum) * 100).toFixed(0) })}
                </p>
              )}
              <button
                type="button"
                disabled={confirm.isPending}
                onClick={() => confirm.mutate()}
                className="w-full rounded-xl bg-tg-button py-2 text-sm font-semibold text-tg-buttonText disabled:opacity-50"
              >
                {confirm.isPending ? t('wd.withdrawing') : t('wd.confirm')}
              </button>
            </div>
          )}
        </>
      )}

      {error && <p className="text-xs text-rose-500">{error}</p>}
      {done && <p className="text-xs text-emerald-500">{done}</p>}
    </div>
  );
}
