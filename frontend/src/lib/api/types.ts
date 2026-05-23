export interface UserInfo {
  id: number;
  telegramId: number;
  username?: string;
  firstName?: string;
  lastName?: string;
  photoUrl?: string;
  languageCode?: string;
  premium: boolean;
  walletAddress?: string;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  expiresInSec: number;
  user: UserInfo;
}

export interface Market {
  id?: string;
  conditionId: string;
  question: string;
  slug?: string;
  description?: string;
  category?: string;
  image?: string;
  icon?: string;
  endDate?: string;
  active?: boolean;
  closed?: boolean;
  acceptingOrders?: boolean;
  volume?: number;
  volume24h?: number;
  /** Matches Gamma/backend JSON field `volume24hr`. */
  volume24hr?: number;
  liquidity?: number;
  outcomes?: string[];
  clobTokenIds?: string[];
  outcomePrices?: string[];
}

export interface MarketList {
  items: Market[];
  page: number;
  size: number;
  hasMore: boolean;
}

export interface MarketComment {
  id: string;
  body: string;
  author: string;
  authorAvatar?: string | null;
  createdAt?: string | null;
}

export interface OrderbookLevel {
  price: number;
  size: number;
}

export interface Orderbook {
  market: string;
  assetId: string;
  hash?: string;
  bids: OrderbookLevel[];
  asks: OrderbookLevel[];
  timestamp?: string;
}

export interface PriceHistory {
  history: { t: number; p: number }[];
}

export interface Favorite {
  id: number;
  conditionId: string;
}

/**
 * Open position as returned by `GET /api/positions`. Field set mirrors Polymarket Data API; backend
 * decorates entries with {@link Position.favorite} based on the caller's saved markets.
 */
export interface Position {
  proxyWallet: string;
  /** CLOB token id for this outcome (uint256 decimal string). */
  asset: string;
  conditionId: string;
  size: number;
  avgPrice: number;
  initialValue: number;
  currentValue: number;
  /** Unrealised PnL in USDC (positive when in profit). */
  cashPnl: number;
  /** Same PnL but as a fraction (0.12 == +12%). */
  percentPnl: number;
  totalBought: number;
  realizedPnl: number;
  percentRealizedPnl: number;
  /** Last traded outcome price, 0..1. */
  curPrice: number;
  redeemable: boolean;
  mergeable: boolean;
  title: string;
  slug: string;
  icon?: string;
  eventSlug: string;
  /** Outcome label such as "Yes" or "No". */
  outcome: string;
  outcomeIndex: number;
  oppositeOutcome: string;
  oppositeAsset: string;
  /** ISO-8601 string, may be empty for never-ending markets. */
  endDate: string;
  negativeRisk: boolean;
  favorite?: boolean;
}

export interface TypedDataResponse {
  orderHash: string;
  typedData: Record<string, unknown>;
  /**
   * Optional ERC-20 USDC `transfer(recipient, amount)` to broadcast right before submitting
   * the order. Present when the platform is configured with a non-zero spread / fee wallet.
   */
  feeTx?: ApprovalUnsignedTx | null;
  /** Fee amount in USDC, decimal string (e.g. "0.025"). Null when no fee is charged. */
  feeAmountUsdc?: string | null;
  /** Basis points actually applied (echoes server config). Null when no fee. */
  feeBps?: number | null;
}

export interface SubmittedOrder {
  orderId: string;
  status: string;
  txHash?: string;
}

export interface ApiError {
  code: string;
  message: string;
  timestamp: string;
  traceId?: string;
}

/** L1 prepare response — wallet signs `typedData`, then submits with the echoed timestamp/nonce. */
export interface ClobAuthPrepareResponse {
  address: string;
  timestamp: number;
  nonce: number;
  digestHex: string;
  typedData: Record<string, unknown>;
}

export interface ClobAuthStatus {
  configured: boolean;
}

export interface ApprovalUnsignedTx {
  kind:
    | 'USDC_APPROVE'
    | 'CTF_SET_APPROVAL_FOR_ALL'
    | 'CTF_REDEEM_POSITIONS'
    | 'TRADING_FEE_TRANSFER';
  to: string;
  data: string;
  value: string;
  chainId: number;
}

export interface ApprovalAllowanceState {
  spender: string;
  allowance: string | null;
  approvedForAll: boolean;
  approvedForAllKnown: boolean | null;
}

export interface ApprovalStatus {
  wallet: string;
  spender: string;
  usdc: ApprovalAllowanceState;
  ctf: ApprovalAllowanceState;
  missing: ApprovalUnsignedTx[];
}

export type OrderSignatureType = 'EOA' | 'POLY_PROXY' | 'POLY_GNOSIS_SAFE';

/** Public read-only platform fee configuration (`GET /api/fees`). */
export interface FeeConfig {
  enabled: boolean;
  /** Basis points; 100 = 1%, 50 = 0.5%. Always 0 when {@link enabled} is false. */
  spreadBps: number;
  recipient: string | null;
}

/** Response from `POST /api/positions/redeem/prepare` — wallet broadcasts {@link RedeemPrepareResponse.tx}. */
export interface RedeemPrepareResponse {
  conditionId: string;
  outcomeIndex: number;
  /** Index sets actually burned (decimal strings, typically `["1"]` or `["2"]`). */
  indexSets: string[];
  tx: ApprovalUnsignedTx;
}
