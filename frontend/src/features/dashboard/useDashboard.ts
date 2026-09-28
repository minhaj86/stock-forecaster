import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { analyticsApi } from '@/api/analyticsApi';
import { queryKeys } from '@/api/queryKeys';
import { pollWhilePending } from '@/features/stocks/hooks';
import type { MarketPeriod } from '@/models/marketData';

export function useDashboard(period: MarketPeriod, watchlistId: string | null) {
  return useQuery({
    queryKey: queryKeys.dashboard(period, watchlistId),
    queryFn: () => analyticsApi.dashboard(period, watchlistId ?? undefined),
    placeholderData: keepPreviousData,
    refetchInterval: (query) => pollWhilePending(query.state.data?.stocks),
  });
}
