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

export interface Position {
  conditionId: string;
  tokenId: string;
  outcome: string;
  size: number;
  avgPrice: number;
  currentValue: number;
  pnl: number;
}

export interface TypedDataResponse {
  orderHash: string;
  typedData: Record<string, unknown>;
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
  kind: 'USDC_APPROVE' | 'CTF_SET_APPROVAL_FOR_ALL';
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
