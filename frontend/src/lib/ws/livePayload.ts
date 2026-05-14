export type PriceChangeEntry = {
  asset_id: string;
  price: string;
  size: string;
  side: string;
  hash?: string;
  best_bid?: string;
  best_ask?: string;
};

export type PriceChangeMessage = {
  event_type?: string;
  market?: string;
  timestamp?: string;
  price_changes?: PriceChangeEntry[];
};

export function isPriceChangeMessage(x: unknown): x is PriceChangeMessage {
  return (
    typeof x === 'object' &&
    x !== null &&
    (x as PriceChangeMessage).event_type === 'price_change' &&
    Array.isArray((x as PriceChangeMessage).price_changes)
  );
}
