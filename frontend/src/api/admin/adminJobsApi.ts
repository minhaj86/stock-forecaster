import type { JobSubmission, MarketDataJob, MarketDataJobFilters } from '@/models/admin';
import type { Page } from '@/models/api';
import { httpClient } from '../httpClient';
import { compactParams } from './params';

const path = (jobId: string) => `/admin/market-data/jobs/${encodeURIComponent(jobId)}`;

export const adminJobsApi = {
  list: async (filters: MarketDataJobFilters): Promise<Page<MarketDataJob>> =>
    (await httpClient.get<Page<MarketDataJob>>('/admin/market-data/jobs', { params: compactParams({ ...filters }) })).data,

  get: async (jobId: string): Promise<MarketDataJob> => (await httpClient.get<MarketDataJob>(path(jobId))).data,

  /** Creates a new job (with `retryOfJobId`) for a retryable failed job. */
  retry: async (jobId: string): Promise<JobSubmission> =>
    (await httpClient.post<JobSubmission>(`${path(jobId)}/retry`)).data,

  /** Only queued jobs can be cancelled. */
  cancel: async (jobId: string): Promise<MarketDataJob> =>
    (await httpClient.post<MarketDataJob>(`${path(jobId)}/cancel`)).data,
};
