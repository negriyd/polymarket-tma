import { isWalletConfigured } from '@/features/wallet/useWallet';
import { WalletPanel } from './_WalletPanel';
import { useT } from '@/lib/i18n';

export function WalletPage() {
  const t = useT();
  if (!isWalletConfigured()) {
    return (
      <div className="space-y-3 pt-4 text-center text-sm text-tg-hint max-w-full min-w-0">
        <h1 className="text-2xl font-semibold text-tg-text">{t('wallet.title')}</h1>
        <p className="rounded-xl bg-tg-secondary p-4">
          {t('wallet.disabled', { env: 'VITE_PRIVY_APP_ID' })}
        </p>
      </div>
    );
  }
  return <WalletPanel />;
}
