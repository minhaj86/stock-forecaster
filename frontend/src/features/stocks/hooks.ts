import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { analyticsApi } from '@/api/analyticsApi';
import { marketDataApi } from '@/api/marketDataApi';
import { queryKeys } from '@/api/queryKeys';
import { stocksApi } from '@/api/stocksApi';
import type { StockSnapshot } from '@/models/analytics';
import type { MarketPeriod } from '@/models/marketData';

/** While market data is still being collected, poll until it is ready. */
const POLL_WHILE_PENDING_MS = 3_000;

export function pollWhilePending(snapshots: StockSnapshot[] | undefined): number | false {
  return snapshots?.some((snapshot) => snapshot.dataStatus === 'PENDING') ? POLL_WHILE_PENDING_MS : false;
}

export function useStockSearch(query: string) {
  const trimmed = query.trim();
  return useQuery({
    queryKey: queryKeys.stockSearch(trimmed.toUpperCase()),
    queryFn: ({ signal }) => stocksApi.search(trimmed, 8, signal),
    enabled: trimmed.length > 0,
    staleTime: 10 * 60_000,
    placeholderData: keepPreviousData,
  });
}

export function useCatalogue(sector: string | null, page: number) {
  return useQuery({
    queryKey: queryKeys.catalogue(sector, page),
    queryFn: () => stocksApi.browse(sector, page),
    staleTime: 10 * 60_000,
    placeholderData: keepPreviousData,
  });
}

export function useSectors() {
  return useQuery({ queryKey: queryKeys.sectors, queryFn: stocksApi.sectors, staleTime: 60 * 60_000 });
}

export function useStock(symbol: string) {
  return useQuery({ queryKey: queryKeys.stock(symbol), queryFn: () => stocksApi.get(symbol), staleTime: 60 * 60_000 });
}

export function useStockAnalytics(symbol: string) {
  return useQuery({
    queryKey: queryKeys.stockAnalytics(symbol),
    queryFn: () => analyticsApi.stock(symbol),
    refetchInterval: (query) => pollWhilePending(query.state.data ? [query.state.data] : undefined),
  });
}

export function useSnapshots(symbols: string[]) {
  return useQuery({
    queryKey: queryKeys.snapshots(symbols),
    queryFn: () => analyticsApi.snapshots(symbols),
    enabled: symbols.length > 0,
    placeholderData: keepPreviousData,
    refetchInterval: (query) => pollWhilePending(query.state.data),
  });
}

export function useStockPrices(symbol: string, period: MarketPeriod) {
  return useQuery({
    queryKey: queryKeys.prices(symbol, period),
    queryFn: () => marketDataApi.prices(symbol, period),
    placeholderData: keepPreviousData,
    // A just-watched stock reports PENDING until its history is collected.
    refetchInterval: (query) => (query.state.data?.dataStatus === 'PENDING' ? POLL_WHILE_PENDING_MS : false),
  });
}

export function useMarketDataProvider() {
  return useQuery({ queryKey: queryKeys.provider, queryFn: marketDataApi.provider, staleTime: Infinity });
}
