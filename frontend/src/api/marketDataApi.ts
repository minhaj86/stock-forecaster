import type { MarketPeriod, PriceHistory, ProviderInfo } from '@/models/marketData';
import { httpClient } from './httpClient';

export const marketDataApi = {
  prices: async (symbol: string, period: MarketPeriod): Promise<PriceHistory> =>
    (await httpClient.get<PriceHistory>(`/stocks/${encodeURIComponent(symbol)}/prices`, { params: { period } })).data,

  provider: async (): Promise<ProviderInfo> => (await httpClient.get<ProviderInfo>('/market-data/provider')).data,
};
