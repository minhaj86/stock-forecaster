import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminProvidersApi } from '@/api/admin/adminProvidersApi';
import { queryKeys } from '@/api/queryKeys';

export function useMarketDataProviders() {
  return useQuery({ queryKey: queryKeys.admin.providers, queryFn: adminProvidersApi.overview });
}

/** The last known health of the active provider (cached by the backend). */
export function useProviderHealth() {
  return useQuery({ queryKey: queryKeys.admin.providerHealth, queryFn: () => adminProvidersApi.health(false) });
}

/** Runs an active health check now, then refetches the provider overview and the admin overview. */
export function useRefreshProviderHealth() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => adminProvidersApi.health(true),
    onSuccess: (health) => {
      queryClient.setQueryData(queryKeys.admin.providerHealth, health);
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.admin.providers }),
        queryClient.invalidateQueries({ queryKey: queryKeys.admin.dashboard }),
      ]);
    },
  });
}
