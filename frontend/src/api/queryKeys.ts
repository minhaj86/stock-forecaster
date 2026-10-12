import type { AdminStockFilters, AdminUserFilters, AuditLogFilters, MarketDataJobFilters } from '@/models/admin';
import type { ForecastSettings } from '@/models/forecast';
import type { MarketPeriod } from '@/models/marketData';

/** Admin console keys share the `admin` prefix; the `…All` keys invalidate a whole area. */
const adminKeys = {
  all: ['admin'] as const,
  dashboard: ['admin', 'dashboard'] as const,
  usersAll: ['admin', 'users'] as const,
  users: (filters: AdminUserFilters) => ['admin', 'users', 'list', filters] as const,
  user: (id: string) => ['admin', 'users', 'detail', id] as const,
  userStatistics: ['admin', 'users', 'statistics'] as const,
  /** Also covers provider-search results, whose "already listed" flags change when stocks are added. */
  stocksAll: ['admin', 'stocks'] as const,
  stocks: (filters: AdminStockFilters) => ['admin', 'stocks', 'list', filters] as const,
  stock: (symbol: string) => ['admin', 'stocks', 'detail', symbol] as const,
  providerSearch: (query: string) => ['admin', 'stocks', 'provider-search', query] as const,
  jobsAll: ['admin', 'jobs'] as const,
  jobs: (filters: MarketDataJobFilters) => ['admin', 'jobs', 'list', filters] as const,
  job: (jobId: string) => ['admin', 'jobs', 'detail', jobId] as const,
  providersAll: ['admin', 'providers'] as const,
  providers: ['admin', 'providers', 'overview'] as const,
  providerHealth: ['admin', 'providers', 'health'] as const,
  auditAll: ['admin', 'audit'] as const,
  audit: (filters: AuditLogFilters) => ['admin', 'audit', filters] as const,
};

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
  /** Catalogue, search and details of the user-facing stock pages. */
  stocksAll: ['stocks'] as const,
  snapshots: (symbols: string[]) => ['analytics', 'snapshots', [...symbols].sort().join(',')] as const,
  stockAnalytics: (symbol: string) => ['analytics', 'stock', symbol] as const,
  analyticsAll: ['analytics'] as const,
  prices: (symbol: string, period: MarketPeriod) => ['prices', symbol, period] as const,
  pricesAll: ['prices'] as const,
  forecast: (symbol: string, settings: ForecastSettings) => ['forecast', 'result', symbol, settings] as const,
  forecastModels: (symbol: string) => ['forecast', 'models', symbol] as const,
  forecastAll: ['forecast'] as const,
  admin: adminKeys,
};
