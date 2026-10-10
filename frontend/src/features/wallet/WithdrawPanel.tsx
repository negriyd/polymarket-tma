import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSignTypedData, type SignTypedDataParams } from '@privy-io/react-auth';
import { AxiosError } from 'axios';
import { formatUnits, parseUnits } from 'viem';
import { api } from '@/lib/api/endpoints';
import type { ApiError, WithdrawDestination, WithdrawPrepared } from '@/lib/api/types';
import { signAndRelayBatch, waitForRelayerTx } from './relayerTx';

interface Props {
  walletAddress: string;
}

function formatErr(e: unknown): string {
  if (e instanceof AxiosError) {
    const d = e.response?.data as ApiError | undefined;
    if (d?.message) return d.message;
    if (e.message) return e.message;
  }
  if (e instanceof Error) return e.message;
  return 'Something went wrong';
}

const key = (d: WithdrawDestination) => `${d.chainId}:${d.tokenAddress}`;

/**
 * Withdraw pUSD from the deposit wallet: pUSD on Polygon directly, or a stablecoin on another
 * network via the Polymarket bridge. Two steps — prepare shows the bridge's estimate, then the
 * owner signs the batch — because small bridged withdrawals can lose a large share to fill costs.
 */
export function WithdrawPanel({ walletAddress }: Props) {
  const qc = useQueryClient();
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
      if (!dest) throw new Error('Choose where to withdraw');
      const amount = parseUnits(amountStr.trim() || '0', 6);
      if (amount <= 0n) throw new Error('Enter an amount');
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
      if (!prepared) throw new Error('Nothing to confirm');
      const tx = await signAndRelayBatch(prepared.batch, async (td) =>
        (await signTypedData(td as SignTypedDataParams, { address: walletAddress })).signature,
      );
      if (!tx) throw new Error('Empty withdrawal batch');
      return waitForRelayerTx(tx);
    },
    onSuccess: (tx) => {
      setPrepared(null);
      setAmountStr('');
      setDone(
        prepared?.route === 'BRIDGE'
          ? `Sent to the bridge (tx ${tx.transactionHash?.slice(0, 10) ?? tx.transactionId}…). Funds arrive shortly.`
          : `Withdrawn (tx ${tx.transactionHash?.slice(0, 10) ?? tx.transactionId}…).`,
      );
      qc.invalidateQueries({ queryKey: ['deposit-wallet'] });
      qc.invalidateQueries({ queryKey: ['withdraw-options'] });
    },
    onError: (e) => setError(formatErr(e)),
  });

  if (options.isLoading) return <p className="text-xs text-tg-hint">Loading withdrawal options…</p>;
  if (options.isError || !options.data) {
    return <p className="text-xs text-rose-500">Withdrawal options are unavailable right now.</p>;
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
            placeholder={`Recipient ${dest.chainName} address`}
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
              placeholder={`Amount in pUSD${dest.minUsd != null ? ` (min ${dest.minUsd})` : ''}`}
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
                Max
              </button>
            )}
          </div>
          {dest.route === 'DIRECT' && (
            <p className="text-[10px] leading-snug text-amber-500">
              Sends pUSD itself. Many exchanges don&apos;t accept pUSD deposits — for an exchange, pick
              USDC or USDT instead.
            </p>
          )}

          {!prepared ? (
            <button
              type="button"
              disabled={prepare.isPending || !recipient.trim() || !amountStr}
              onClick={() => prepare.mutate()}
              className="w-full rounded-xl bg-tg-bg py-2 text-sm font-semibold text-tg-text disabled:opacity-50"
            >
              {prepare.isPending ? 'Checking…' : 'Review withdrawal'}
            </button>
          ) : (
            <div className="space-y-2 rounded-lg bg-tg-bg p-2 text-xs text-tg-text">
              <p>
                Send {amountStr} pUSD →{' '}
                {received != null ? `≈ ${received.toFixed(2)} ${dest.symbol}` : dest.symbol} on {dest.chainName}
                {prepared.estDurationMs ? ` · ~${Math.ceil(prepared.estDurationMs / 1000)} s` : ''}
              </p>
              {expensive && (
                <p className="text-rose-500">
                  Bridge costs take {(100 - (received! / sentNum) * 100).toFixed(0)}% of this amount. Larger
                  withdrawals, or pUSD on Polygon, lose much less.
                </p>
              )}
              <button
                type="button"
                disabled={confirm.isPending}
                onClick={() => confirm.mutate()}
                className="w-full rounded-xl bg-tg-button py-2 text-sm font-semibold text-tg-buttonText disabled:opacity-50"
              >
                {confirm.isPending ? 'Withdrawing…' : 'Confirm & sign'}
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
