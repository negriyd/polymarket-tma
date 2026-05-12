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
