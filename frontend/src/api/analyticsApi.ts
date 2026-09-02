import type { Dashboard, Performance, StockSnapshot } from '@/models/analytics';
import type { MarketPeriod } from '@/models/marketData';
import { httpClient } from './httpClient';

export const analyticsApi = {
  snapshots: async (symbols: string[]): Promise<StockSnapshot[]> =>
    (await httpClient.get<StockSnapshot[]>('/analytics/snapshots', { params: { symbols: symbols.join(',') } })).data,

  stock: async (symbol: string): Promise<StockSnapshot> =>
    (await httpClient.get<StockSnapshot>(`/stocks/${encodeURIComponent(symbol)}/analytics`)).data,

  performance: async (symbols: string[], period: MarketPeriod): Promise<Performance> =>
    (await httpClient.get<Performance>('/analytics/performance', { params: { symbols: symbols.join(','), period } }))
      .data,

  dashboard: async (period: MarketPeriod, watchlistId?: string): Promise<Dashboard> =>
    (await httpClient.get<Dashboard>('/dashboard', { params: { period, watchlistId } })).data,
};
