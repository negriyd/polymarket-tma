import { http } from './client';
import type {
  ApprovalStatus,
  ClobAuthPrepareResponse,
  ClobAuthStatus,
  DepositWalletStatus,
  Favorite,
  Funding,
  FeeConfig,
  Market,
  MarketEvent,
  MarketShare,
  ReferralInfo,
  MarketList,
  MarketComment,
  OrderSignatureType,
  Orderbook,
  Position,
  PreparedBatch,
  PriceHistory,
  Bet,
  BridgeDeposit,
  BetBalance,
  BetPool,
  BetsConfig,
  BetWithdrawal,
  PrepareBetResponse,
  RedeemPrepareResponse,
  RelayerTx,
  SubmittedOrder,
  TokenPair,
  TypedDataResponse,
  UserInfo,
  WithdrawOptions,
  WithdrawPrepared,
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

  getMyReferrals: () => http.get<ReferralInfo>('/api/referrals/me').then((r) => r.data),

  shareMarket: (body: { marketKey: string; side?: 'yes' | 'no' }) =>
    http.post<MarketShare>('/api/share/market', body).then((r) => r.data),

  getMarketEvent: (marketKey: string) =>
    http.get<MarketEvent>(`/api/markets/${encodeURIComponent(marketKey)}/event`).then((r) => r.data),

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

  /** CLOB v2 deposit wallet: derived address, deployment, pUSD balance, approvals. */
  getDepositWallet: () =>
    http.get<DepositWalletStatus>('/api/wallet/deposit-wallet').then((r) => r.data),

  /** Gasless WALLET-CREATE via the Polymarket relayer; poll {@link getRelayerTx} until mined. */
  deployDepositWallet: () =>
    http.post<RelayerTx>('/api/wallet/deposit-wallet/deploy').then((r) => r.data),

  /** Batch with every missing approval; sign `typedData` with Privy, then submit. */
  prepareDepositWalletApprovals: () =>
    http.post<PreparedBatch>('/api/wallet/deposit-wallet/approvals/prepare').then((r) => r.data),

  /** Relay any server-prepared deposit-wallet batch (approvals, platform fee, Claim) once signed. */
  submitDepositWalletBatch: (body: { batchHash: string; signature: string }) =>
    http.post<RelayerTx>('/api/wallet/deposit-wallet/batches/submit', body).then((r) => r.data),

  getRelayerTx: (transactionId: string) =>
    http
      .get<RelayerTx>(`/api/wallet/deposit-wallet/transactions/${encodeURIComponent(transactionId)}`)
      .then((r) => r.data),

  /** Polymarket bridge addresses + supported stablecoins for topping up the deposit wallet. */
  getFunding: () =>
    http.get<Funding>('/api/wallet/deposit-wallet/funding').then((r) => r.data),

  listBridgeDeposits: () =>
    http.get<BridgeDeposit[]>('/api/wallet/deposit-wallet/funding/deposits').then((r) => r.data),

  getWithdrawOptions: () =>
    http.get<WithdrawOptions>('/api/wallet/deposit-wallet/withdraw/options').then((r) => r.data),

  /** Builds the pUSD transfer batch (direct or to a bridge address); submit via submitDepositWalletBatch. */
  prepareWithdraw: (body: { toChainId: string; toTokenAddress: string; recipient: string; amountBaseUnit: string }) =>
    http.post<WithdrawPrepared>('/api/wallet/deposit-wallet/withdraw/prepare', body).then((r) => r.data),

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

  /** Claim for CLOB v2 positions (held by the deposit wallet): batch paying out pUSD. */
  prepareRedeemBatch: (body: { conditionId: string; negRisk: boolean }) =>
    http.post<PreparedBatch>('/api/positions/redeem/prepare-batch', body).then((r) => r.data),

  /** Public platform-fee configuration (used to render a preview before signing). */
  getFeeConfig: () => http.get<FeeConfig>('/api/fees').then((r) => r.data),

  getBetsConfig: () => http.get<BetsConfig>('/api/bets/config').then((r) => r.data),

  getBetPool: (conditionId: string) =>
    http.get<BetPool>(`/api/bets/pool/${encodeURIComponent(conditionId)}`).then((r) => r.data),

  prepareBet: (body: {
    conditionId: string;
    outcomeIndex: number;
    amountUsdc: number;
    idempotency_key?: string;
  }) => http.post<PrepareBetResponse>('/api/bets/prepare', body).then((r) => r.data),

  confirmBet: (body: { betId: number; paymentTxHash: string }) =>
    http.post<Bet>('/api/bets/confirm', body).then((r) => r.data),

  listMyBets: () => http.get<Bet[]>('/api/bets/mine').then((r) => r.data),

  getBetBalance: () => http.get<BetBalance>('/api/bets/balance').then((r) => r.data),

  withdrawBetBalance: (body: { amountUsdc: number }) =>
    http.post<BetWithdrawal>('/api/bets/withdraw', body).then((r) => r.data),

  listBetWithdrawals: () => http.get<BetWithdrawal[]>('/api/bets/withdrawals').then((r) => r.data),
};
