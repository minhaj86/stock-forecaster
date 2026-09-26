import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { queryKeys } from '@/api/queryKeys';
import { watchlistApi } from '@/api/watchlistApi';

/**
 * Watchlist changes alter the dashboard and whether market data is collected for a stock (its analytics, price
 * history and forecasts move from "not tracked" to "pending"), so all of them are refetched.
 */
function invalidateWatchlistData(queryClient: QueryClient) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: queryKeys.watchlists }),
    queryClient.invalidateQueries({ queryKey: queryKeys.dashboardAll }),
    queryClient.invalidateQueries({ queryKey: queryKeys.analyticsAll }),
    queryClient.invalidateQueries({ queryKey: queryKeys.pricesAll }),
    queryClient.invalidateQueries({ queryKey: queryKeys.forecastAll }),
  ]);
}

export function useWatchlists() {
  return useQuery({ queryKey: queryKeys.watchlists, queryFn: watchlistApi.list });
}

export function useWatchlist(id: string) {
  return useQuery({ queryKey: queryKeys.watchlist(id), queryFn: () => watchlistApi.get(id) });
}

export function useCreateWatchlist() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => watchlistApi.create(name),
    onSuccess: () => invalidateWatchlistData(queryClient),
  });
}

export function useRenameWatchlist() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) => watchlistApi.rename(id, name),
    onSuccess: () => invalidateWatchlistData(queryClient),
  });
}

export function useDeleteWatchlist() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => watchlistApi.remove(id),
    onSuccess: (_, id) => {
      queryClient.removeQueries({ queryKey: queryKeys.watchlist(id) });
      return invalidateWatchlistData(queryClient);
    },
  });
}

export function useAddStock() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ watchlistId, symbol }: { watchlistId: string; symbol: string }) =>
      watchlistApi.addStock(watchlistId, symbol),
    onSuccess: () => invalidateWatchlistData(queryClient),
  });
}

export function useRemoveStock() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ watchlistId, symbol }: { watchlistId: string; symbol: string }) =>
      watchlistApi.removeStock(watchlistId, symbol),
    onSuccess: () => invalidateWatchlistData(queryClient),
  });
}
