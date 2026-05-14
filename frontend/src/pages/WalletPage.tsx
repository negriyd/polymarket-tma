import { isWalletConfigured } from '@/features/wallet/useWallet';
import { WalletPanel } from './_WalletPanel';

export function WalletPage() {
  if (!isWalletConfigured()) {
    return (
      <div className="space-y-3 pt-4 text-center text-sm text-tg-hint max-w-full min-w-0">
        <h1 className="text-2xl font-semibold text-tg-text">Wallet</h1>
        <p className="rounded-xl bg-tg-secondary p-4">
          Wallet features are disabled. Set <code>VITE_PRIVY_APP_ID</code> to enable embedded wallets and trading.
        </p>
      </div>
    );
  }
  return <WalletPanel />;
}
