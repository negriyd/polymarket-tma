import { http } from './client';
import type {
  ApprovalStatus,
  ClobAuthPrepareResponse,
  ClobAuthStatus,
  Favorite,
  FeeConfig,
  Market,
  MarketList,
  MarketComment,
  OrderSignatureType,
  Orderbook,
  Position,
  PriceHistory,
  RedeemPrepareResponse,
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
    signatureType?: OrderSignatureType;
    makerAddress?: string;
  }) => http.post<TypedDataResponse>('/api/orders/prepare', body).then((r) => r.data),

  submitOrder: (body: { orderHash: string; signature: string; idempotency_key?: string }) =>
    http.post<SubmittedOrder>('/api/orders/submit', body).then((r) => r.data),

  cancelOrder: (orderId: string) =>
    http.delete(`/api/orders/${orderId}`).then(() => undefined),

  /** L1 derivation flow: prepare → sign typedData with Privy → submit { signature, timestamp, nonce }. */
  clobAuthPrepare: () =>
    http.post<ClobAuthPrepareResponse>('/api/clob/auth/prepare').then((r) => r.data),

  clobAuthSubmit: (body: { signature: string; timestamp: number; nonce: number }) =>
    http.post<ClobAuthStatus>('/api/clob/auth/submit', body).then((r) => r.data),

  clobAuthStatus: () =>
    http.get<ClobAuthStatus>('/api/clob/auth/status').then((r) => r.data),

  clobAuthRevoke: () =>
    http.delete('/api/clob/auth').then(() => undefined),

  /** Returns USDC + CTF approval state and a list of unsigned txs the wallet should broadcast. */
  getApprovals: () =>
    http.get<ApprovalStatus>('/api/wallet/approvals').then((r) => r.data),

  /**
   * Build the unsigned {@code redeemPositions} tx for a settled market. Frontend dispatches the
   * returned {@link RedeemPrepareResponse.tx} via Privy and the user's USDC balance reflects the
   * payout once the tx is mined.
   */
  prepareRedeem: (body: { conditionId: string; outcomeIndex: number }) =>
    http.post<RedeemPrepareResponse>('/api/positions/redeem/prepare', body).then((r) => r.data),

  /** Public platform-fee configuration (used to render a preview before signing). */
  getFeeConfig: () => http.get<FeeConfig>('/api/fees').then((r) => r.data),
};
