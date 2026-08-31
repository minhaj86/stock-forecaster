import type { MarketPeriod } from './marketData';

export type DataStatus = 'READY' | 'PENDING' | 'NOT_TRACKED' | 'UNAVAILABLE';

/** Metrics computed by the backend. Ratios are decimal fractions (0.052 = 5.2 %); null = not enough history. */
export interface StockSnapshot {
  symbol: string;
  companyName: string;
  exchange: string;
  currency: string;
  sector: string | null;
  dataStatus: DataStatus;
  dataProvider: string | null;
  asOf: string | null;
  lastSyncedAt: string | null;
  syncIssue: string | null;
  price: number | null;
  previousClose: number | null;
  change: number | null;
  changePercent: number | null;
  return1W: number | null;
  return1M: number | null;
  return3M: number | null;
  return6M: number | null;
  returnYtd: number | null;
  return1Y: number | null;
  volatility30d: number | null;
  volatility90d: number | null;
  maxDrawdown1Y: number | null;
  rsi14: number | null;
  momentum3M: number | null;
}

export interface PerformancePoint {
  date: string;
  value: number;
}

export interface PerformanceSeries {
  symbol: string;
  companyName: string;
  baseDate: string;
  /** History starts after the period start; the first available close is the base. */
  partial: boolean;
  periodReturn: number;
  points: PerformancePoint[];
}

export interface Performance {
  period: MarketPeriod;
  asOf: string | null;
  series: PerformanceSeries[];
}

export interface Mover {
  symbol: string;
  companyName: string;
  value: number;
}

export interface Movers {
  topPerformer: Mover | null;
  weakestPerformer: Mover | null;
  highestVolatility: Mover | null;
  lowestVolatility: Mover | null;
  strongestMomentum: Mover | null;
}

export interface Dashboard {
  generatedAt: string;
  period: MarketPeriod;
  watchlistCount: number;
  stockCount: number;
  truncated: boolean;
  dataAsOf: string | null;
  lastSyncedAt: string | null;
  dataProviders: string[];
  stocks: StockSnapshot[];
  movers: Movers;
  performance: Performance;
}
