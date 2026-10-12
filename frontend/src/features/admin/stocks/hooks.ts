import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminStocksApi } from '@/api/admin/adminStocksApi';
import { queryKeys } from '@/api/queryKeys';
import type { AdminStock, AdminStockFilters, CreateStockRequest } from '@/models/admin';
import { invalidateAdminStocks } from '../invalidation';

/** A stock with a queued or running job is followed closely; lists refresh a little less often. */
const POLL_STOCK_WITH_JOB_MS = 3_000;
const POLL_LIST_WITH_JOBS_MS = 5_000;
const PICKER_SIZE = 20;

export function useAdminStocks(filters: AdminStockFilters) {
  return useQuery({
    queryKey: queryKeys.admin.stocks(filters),
    queryFn: ({ signal }) => adminStocksApi.list(filters, signal),
    placeholderData: keepPreviousData,
    refetchInterval: (query) =>
      (query.state.data?.content.some((stock) => stock.marketData.activeJob) ? POLL_LIST_WITH_JOBS_MS : false),
  });
}

/** Options for stock pickers: catalogue entries matching the typed text (no polling needed). */
export function useStockOptions(query: string) {
  const filters: AdminStockFilters = { query: query.trim() || undefined, page: 0, size: PICKER_SIZE };
  return useQuery({
    queryKey: queryKeys.admin.stocks(filters),
    queryFn: ({ signal }) => adminStocksApi.list(filters, signal),
    placeholderData: keepPreviousData,
    staleTime: 30_000,
  });
}

export function useAdminStock(symbol: string) {
  return useQuery({
    queryKey: queryKeys.admin.stock(symbol),
    queryFn: () => adminStocksApi.get(symbol),
    enabled: symbol.length > 0,
    refetchInterval: (query) => (query.state.data?.marketData.activeJob ? POLL_STOCK_WITH_JOB_MS : false),
  });
}

/** Searches the provider's instruments. Provider errors (429/503) are shown instead of being retried. */
export function useProviderSearch(query: string) {
  const trimmed = query.trim();
  return useQuery({
    queryKey: queryKeys.admin.providerSearch(trimmed.toUpperCase()),
    queryFn: ({ signal }) => adminStocksApi.providerSearch(trimmed, signal),
    enabled: trimmed.length > 0,
    staleTime: 60_000,
    retry: false,
    placeholderData: keepPreviousData,
  });
}

export function useCreateStock() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateStockRequest) => adminStocksApi.create(request),
    onSuccess: ({ stock, job }) => {
      queryClient.setQueryData(queryKeys.admin.stock(stock.symbol), stock);
      return Promise.all([
        invalidateAdminStocks(queryClient),
        // A requested history load is a new job.
        job ? queryClient.invalidateQueries({ queryKey: queryKeys.admin.jobsAll }) : undefined,
      ]);
    },
  });
}

function useStockStatusMutation(mutationFn: (symbol: string) => Promise<AdminStock>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (stock) => {
      queryClient.setQueryData(queryKeys.admin.stock(stock.symbol), stock);
      return invalidateAdminStocks(queryClient);
    },
  });
}

export function useActivateStock() {
  return useStockStatusMutation((symbol: string) => adminStocksApi.activate(symbol));
}

export function useDeactivateStock() {
  return useStockStatusMutation((symbol: string) => adminStocksApi.deactivate(symbol));
}
