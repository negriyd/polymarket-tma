import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { useTelegramAuth } from '@/features/auth/useTelegramAuth';
import { useAuthStore } from '@/features/auth/store';
import { AppLayout } from './AppLayout';
import { HomePage } from '@/pages/HomePage';
import { MarketsPage } from '@/pages/MarketsPage';
import { MarketDetailPage } from '@/pages/MarketDetailPage';
import { FavoritesPage } from '@/pages/FavoritesPage';
import { WalletPage } from '@/pages/WalletPage';
import { WalletProvider } from '@/features/wallet/PrivyProvider';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
  },
});

function AuthGate({ children }: { children: React.ReactNode }) {
  useTelegramAuth();
  const status = useAuthStore((s) => s.status);
  const error = useAuthStore((s) => s.error);
  if (status === 'authenticated') return <>{children}</>;
  if (status === 'error') {
    return (
      <div className="flex h-full items-center justify-center px-6 text-center">
        <div>
          <p className="text-lg font-semibold">Authentication failed</p>
          <p className="mt-2 text-sm text-tg-hint">{error}</p>
        </div>
      </div>
    );
  }
  return (
    <div className="flex h-full items-center justify-center">
      <div className="h-8 w-8 animate-spin rounded-full border-2 border-tg-button border-t-transparent" />
    </div>
  );
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <WalletProvider>
        <BrowserRouter>
          <AuthGate>
            <Routes>
              <Route element={<AppLayout />}>
                <Route index element={<HomePage />} />
                <Route path="markets" element={<MarketsPage />} />
                <Route path="markets/:marketId" element={<MarketDetailPage />} />
                <Route path="favorites" element={<FavoritesPage />} />
                <Route path="wallet" element={<WalletPage />} />
                <Route path="*" element={<Navigate to="/" replace />} />
              </Route>
            </Routes>
          </AuthGate>
        </BrowserRouter>
      </WalletProvider>
    </QueryClientProvider>
  );
}
