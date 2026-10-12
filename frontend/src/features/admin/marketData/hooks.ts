import { useMutation, useQueryClient } from '@tanstack/react-query';
import { adminMarketDataApi } from '@/api/admin/adminMarketDataApi';
import type { BackfillRequest, BulkLoadRequest, RepopulateRequest } from '@/models/admin';
import { invalidateMarketData } from '../invalidation';

/** Every market-data job changes stock data status and, once finished, prices and everything derived from them. */
function useMarketDataMutation<Variables, Result>(mutationFn: (variables: Variables) => Promise<Result>) {
  const queryClient = useQueryClient();
  return useMutation({ mutationFn, onSuccess: () => invalidateMarketData(queryClient) });
}

export function useUpdateLatestData() {
  return useMarketDataMutation((symbol: string) => adminMarketDataApi.updateLatest(symbol));
}

export function useBackfill() {
  return useMarketDataMutation(({ symbol, request }: { symbol: string; request: BackfillRequest }) =>
    adminMarketDataApi.backfill(symbol, request));
}

export function useRepopulate() {
  return useMarketDataMutation(({ symbol, request }: { symbol: string; request: RepopulateRequest }) =>
    adminMarketDataApi.repopulate(symbol, request));
}

export function useBulkLoad() {
  return useMarketDataMutation((request: BulkLoadRequest) => adminMarketDataApi.bulkLoad(request));
}

export function useTriggerSynchronization() {
  return useMarketDataMutation(() => adminMarketDataApi.synchronize());
}
