import { http } from './client';
import type {
  Favorite,
  Market,
  MarketList,
  MarketComment,
  Orderbook,
  Position,
  PriceHistory,
  SubmittedOrder,
  TokenPair,
  TypedDataResponse,
  UserInfo,
} from './types';

export const api = {
  loginTelegram: (initData: string) =>
    http.post<TokenPair>('/api/auth/telegram', { initData }).then((r) => r.data),

  me: () => http.get<UserInfo>('/api/me').then((r) => r.data),

  listMarkets: (params: {
    page?: number;
    size?: number;
    order?: string;
    ascending?: boolean;
    tag?: string;
    search?: string;
  }) =>
    http
      .get<MarketList>('/api/markets', { params })
      .then((r) => r.data),

  getMarket: (marketKey: string) =>
    http.get<Market>(`/api/markets/${marketKey}`).then((r) => r.data),

  listMarketComments: (marketKey: string, page = 0, size = 30) =>
    http
      .get<MarketComment[]>(`/api/markets/${encodeURIComponent(marketKey)}/comments`, {
        params: { page, size },
      })
      .then((r) => r.data),

  getOrderbook: (conditionId: string, tokenId: string) =>
    http
      .get<Orderbook>(`/api/markets/${conditionId}/orderbook`, { params: { token_id: tokenId } })
      .then((r) => r.data),

  getHistory: (conditionId: string, tokenId: string, interval = '1d') =>
    http
      .get<PriceHistory>(`/api/markets/${conditionId}/history`, {
        params: { token_id: tokenId, interval },
      })
      .then((r) => r.data),

  listFavorites: () => http.get<Favorite[]>('/api/favorites').then((r) => r.data),
  addFavorite: (conditionId: string) =>
    http.post<Favorite>('/api/favorites', { conditionId }).then((r) => r.data),
  removeFavorite: (conditionId: string) =>
    http.delete(`/api/favorites/${conditionId}`).then(() => undefined),

  setWalletAddress: (address: string) =>
    http.post<{ address: string }>('/api/wallet/address', { address }).then((r) => r.data),

  listPositions: () => http.get<Position[]>('/api/positions').then((r) => r.data),

  prepareOrder: (body: {
    conditionId: string;
    tokenId: string;
    side: 'BUY' | 'SELL';
    price: number;
    size: number;
  }) => http.post<TypedDataResponse>('/api/orders/prepare', body).then((r) => r.data),

  submitOrder: (body: { orderHash: string; signature: string; idempotency_key?: string }) =>
    http.post<SubmittedOrder>('/api/orders/submit', body).then((r) => r.data),

  cancelOrder: (orderId: string) =>
    http.delete(`/api/orders/${orderId}`).then(() => undefined),
};
