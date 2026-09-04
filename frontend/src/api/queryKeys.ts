import type { ForecastSettings } from '@/models/forecast';
import type { MarketPeriod } from '@/models/marketData';

/** Central query keys so invalidation after mutations stays consistent. */
export const queryKeys = {
  currentUser: ['currentUser'] as const,
  provider: ['marketData', 'provider'] as const,
  dashboard: (period: MarketPeriod, watchlistId: string | null) => ['dashboard', period, watchlistId ?? 'all'] as const,
  dashboardAll: ['dashboard'] as const,
  watchlists: ['watchlists'] as const,
  watchlist: (id: string) => ['watchlists', id] as const,
  stockSearch: (query: string) => ['stocks', 'search', query] as const,
  catalogue: (sector: string | null, page: number) => ['stocks', 'catalogue', sector ?? 'all', page] as const,
  sectors: ['stocks', 'sectors'] as const,
  stock: (symbol: string) => ['stocks', 'detail', symbol] as const,
  snapshots: (symbols: string[]) => ['analytics', 'snapshots', [...symbols].sort().join(',')] as const,
  stockAnalytics: (symbol: string) => ['analytics', 'stock', symbol] as const,
  analyticsAll: ['analytics'] as const,
  prices: (symbol: string, period: MarketPeriod) => ['prices', symbol, period] as const,
  pricesAll: ['prices'] as const,
  forecast: (symbol: string, settings: ForecastSettings) => ['forecast', 'result', symbol, settings] as const,
  forecastModels: (symbol: string) => ['forecast', 'models', symbol] as const,
  forecastAll: ['forecast'] as const,
};
