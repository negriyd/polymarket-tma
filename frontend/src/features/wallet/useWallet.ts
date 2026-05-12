import { useCallback, useMemo } from 'react';
import { usePrivy, useWallets } from '@privy-io/react-auth';
import { createPublicClient, formatUnits, http } from 'viem';
import { polygon } from 'viem/chains';
import { useQuery } from '@tanstack/react-query';

const USDC_POLYGON = '0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174' as const;
const ERC20_BALANCE_ABI = [
  {
    name: 'balanceOf',
    type: 'function',
    stateMutability: 'view',
    inputs: [{ name: 'owner', type: 'address' }],
    outputs: [{ name: '', type: 'uint256' }],
  },
] as const;

const publicClient = createPublicClient({
  chain: polygon,
  transport: http(import.meta.env.VITE_POLYGON_RPC_URL),
});

export interface WalletState {
  ready: boolean;
  authenticated: boolean;
  address?: string;
  login: () => Promise<void>;
  logout: () => Promise<void>;
  usdcBalance?: { raw: bigint; formatted: string };
  usdcLoading: boolean;
}

/** Must be rendered inside `WalletProvider` configured with a Privy app id. */
export function useWallet(): WalletState {
  const privy = usePrivy();
  const { wallets } = useWallets();
  const address = wallets[0]?.address;

  const balance = useQuery({
    queryKey: ['usdc', address],
    enabled: !!address,
    refetchInterval: 30_000,
    queryFn: async () => {
      const raw = (await publicClient.readContract({
        address: USDC_POLYGON,
        abi: ERC20_BALANCE_ABI,
        functionName: 'balanceOf',
        args: [address as `0x${string}`],
      })) as bigint;
      return { raw, formatted: formatUnits(raw, 6) };
    },
  });

  const login = useCallback(async () => {
    await privy.login();
  }, [privy]);
  const logout = useCallback(async () => {
    await privy.logout();
  }, [privy]);

  return useMemo<WalletState>(
    () => ({
      ready: privy.ready,
      authenticated: privy.authenticated,
      address,
      login,
      logout,
      usdcBalance: balance.data,
      usdcLoading: balance.isLoading,
    }),
    [privy.ready, privy.authenticated, address, login, logout, balance.data, balance.isLoading],
  );
}

export function isWalletConfigured(): boolean {
  return !!import.meta.env.VITE_PRIVY_APP_ID;
}
