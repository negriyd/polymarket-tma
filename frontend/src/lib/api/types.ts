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
  negRisk?: boolean;
  /** CLOB price step (0.01 or 0.001); off-grid limit prices are rejected. */
  orderPriceMinTickSize?: number;
  /** Smallest order CLOB accepts, in shares. */
  orderMinSize?: number;
  /** Label inside a multi-market event: a date ("Oct 31") or a candidate. */
  groupItemTitle?: string | null;
}

/** A Polymarket event and its open markets (date ladder or candidates). */
export interface MarketEvent {
  id: string;
  slug?: string;
  title: string;
  /** Open markets, in display order. */
  markets: Market[];
  /** Closed markets, latest first. */
  resolved?: Market[] | null;
  /** Other events of the same series (other months); includes the current one. */
  seriesEvents?: SeriesEvent[] | null;
}

export interface SeriesEvent {
  id: string;
  title: string;
  endDate?: string | null;
  closed: boolean;
  current: boolean;
  /** Market to open for this event; null for the current one. */
  marketKey?: string | null;
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
  /**
   * POLY_1271 orders: the fee as a pUSD transfer from the deposit wallet. Sign `typedData` and
   * submit via `submitDepositWalletBatch`. Mutually exclusive with {@link feeTx}.
   */
  feeBatch?: PreparedBatch | null;
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
    | 'TRADING_FEE_TRANSFER'
    | 'BET_USDC_TRANSFER';
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

export type OrderSignatureType = 'EOA' | 'POLY_PROXY' | 'POLY_GNOSIS_SAFE' | 'POLY_1271';

/** One approval a CLOB v2 deposit wallet needs; `approved` is null when the RPC is unavailable. */
export interface DepositWalletApproval {
  kind: 'ERC20_APPROVE' | 'ERC1155_SET_APPROVAL_FOR_ALL';
  token: string;
  spender: string;
  approved: boolean | null;
}

/** `GET /api/wallet/deposit-wallet` — the Polymarket deposit wallet that trades for the Privy EOA. */
export interface DepositWalletStatus {
  owner: string;
  address: string;
  deployed: boolean;
  /** pUSD balance in micro units (6 decimals); null when the RPC is unavailable. */
  collateralBalance: string | null;
  approvals: DepositWalletApproval[];
  readyToTrade: boolean;
}

/** Send `symbol` on `chainName` to `depositAddress`; the bridge credits pUSD to the deposit wallet. */
export interface FundingOption {
  chainId: string;
  chainName: string;
  symbol: string;
  tokenAddress: string;
  decimals: number | null;
  minUsd: number | null;
  depositAddress: string;
}

export interface Funding {
  depositWallet: string;
  bridgeAddresses: { evm: string | null; svm: string | null; tron: string | null; btc: string | null };
  options: FundingOption[];
}

export interface BridgeDeposit {
  fromChainId: string;
  fromTokenAddress: string;
  fromAmountBaseUnit: string;
  status: 'DEPOSIT_DETECTED' | 'PROCESSING' | 'ORIGIN_TX_CONFIRMED' | 'SUBMITTED' | 'COMPLETED' | 'FAILED' | string;
  txHash: string | null;
  createdTimeMs: number | null;
}

/** DIRECT = pUSD on Polygon as is; BRIDGE = swapped by the Polymarket bridge to another chain/token. */
export interface WithdrawDestination {
  route: 'DIRECT' | 'BRIDGE';
  chainId: string;
  chainName: string;
  symbol: string;
  tokenAddress: string;
  decimals: number | null;
  minUsd: number | null;
}

export interface WithdrawOptions {
  depositWallet: string;
  /** pUSD micro units; null when the RPC is unavailable. */
  collateralBalance: string | null;
  destinations: WithdrawDestination[];
}

export interface WithdrawPrepared {
  batch: PreparedBatch;
  route: 'DIRECT' | 'BRIDGE';
  sendTo: string;
  /** Estimated amount received, in the destination token's base units; null if unknown. */
  estReceivedBaseUnit: string | null;
  estDurationMs: number | null;
}

/** Gasless Polymarket relayer transaction (deploy / approvals batch). */
export interface RelayerTx {
  transactionId: string;
  state: string;
  transactionHash: string | null;
}

/** EIP-712 `Batch` to sign with Privy; `callCount === 0` means nothing to approve. */
export interface PreparedBatch {
  batchHash: string | null;
  typedData: Record<string, unknown> | null;
  callCount: number;
}

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

/** Public internal-bets config (`GET /api/bets/config`). */
export interface BetsConfig {
  enabled: boolean;
  platformFeeBps: number;
  minBetUsdc: string;
  minWithdrawUsdc: string;
  lockBeforeEndMinutes: number;
}

export interface BetPoolSide {
  outcomeIndex: number;
  outcomeLabel: string;
  totalUsdc: string;
  impliedProbability: string;
}

export interface BetPool {
  conditionId: string;
  totalPoolUsdc: string;
  platformFeeBps: number;
  sides: BetPoolSide[];
}

export interface PrepareBetResponse {
  betId: number;
  conditionId: string;
  outcomeIndex: number;
  outcomeLabel: string;
  amountUsdc: string;
  platformFeeBps: number;
  paymentTx: ApprovalUnsignedTx;
}

export interface Bet {
  id: number;
  conditionId: string;
  outcomeIndex: number;
  outcomeLabel: string;
  amountUsdc: string;
  status: string;
  paymentTxHash?: string | null;
  createdAt: string;
  confirmedAt?: string | null;
}

export interface BetBalance {
  availableUsdc: string;
  withdrawEnabled: boolean;
}

export interface BetWithdrawal {
  id: number;
  amountUsdc: string;
  status: string;
  txHash?: string | null;
  createdAt: string;
  sentAt?: string | null;
}
