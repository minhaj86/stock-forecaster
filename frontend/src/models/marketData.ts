export type MarketPeriod = '1W' | '5D' | '1M' | '3M' | '6M' | 'YTD' | '1Y' | '5Y' | 'MAX';

export type MarketDataState = 'NOT_TRACKED' | 'PENDING' | 'READY' | 'FAILED';

export interface PriceBar {
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  adjustedClose: number;
  volume: number;
}

export interface PriceHistory {
  symbol: string;
  currency: string;
  period: MarketPeriod;
  interval: string;
  dataStatus: MarketDataState;
  dataProvider: string | null;
  asOf: string | null;
  lastSyncedAt: string | null;
  bars: PriceBar[];
}

export interface ProviderInfo {
  id: string;
  name: string;
  /** Simulated prices that must be labelled as such. */
  synthetic: boolean;
  description: string;
}
