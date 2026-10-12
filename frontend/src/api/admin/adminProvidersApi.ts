import type { ProviderHealth, ProviderOverview } from '@/models/admin';
import { httpClient } from '../httpClient';

export const adminProvidersApi = {
  overview: async (): Promise<ProviderOverview> =>
    (await httpClient.get<ProviderOverview>('/admin/market-data/providers')).data,

  /**
   * The last known health; `refresh` runs an active check now (the backend caches results for about a minute and
   * throttles forced checks).
   */
  health: async (refresh = false): Promise<ProviderHealth> =>
    (await httpClient.get<ProviderHealth>('/admin/market-data/providers/health', { params: { refresh } })).data,
};
