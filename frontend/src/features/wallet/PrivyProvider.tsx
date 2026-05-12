import type { ReactNode } from 'react';
import { PrivyProvider as ExternalPrivyProvider } from '@privy-io/react-auth';
import { polygon } from 'viem/chains';

interface Props {
  children: ReactNode;
}

/**
 * Wrap the app in Privy when a configured app id is present. In dev / MVP mode
 * we render children without Privy so the read-only experience still works
 * without the env variable being set.
 */
export function WalletProvider({ children }: Props) {
  const appId = import.meta.env.VITE_PRIVY_APP_ID;
  if (!appId) return <>{children}</>;
  return (
    <ExternalPrivyProvider
      appId={appId}
      config={{
        loginMethods: ['telegram', 'email', 'sms'],
        defaultChain: polygon,
        supportedChains: [polygon],
        embeddedWallets: {
          createOnLogin: 'users-without-wallets',
          requireUserPasswordOnCreate: false,
        },
        appearance: {
          theme: 'dark',
          accentColor: '#3390ec',
          showWalletLoginFirst: false,
        },
      }}
    >
      {children}
    </ExternalPrivyProvider>
  );
}
