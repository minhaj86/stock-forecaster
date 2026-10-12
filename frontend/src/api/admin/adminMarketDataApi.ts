import type {
  BackfillRequest, BulkLoadRequest, BulkLoadResponse, JobSubmission, RepopulateRequest, SynchronizationResult,
} from '@/models/admin';
import { httpClient } from '../httpClient';
import { adminStockPath } from './adminStocksApi';

const marketDataPath = (symbol: string) => `${adminStockPath(symbol)}/market-data`;

/** Every call queues background jobs; none of them waits for the market-data provider. */
export const adminMarketDataApi = {
  updateLatest: async (symbol: string): Promise<JobSubmission> =>
    (await httpClient.post<JobSubmission>(`${marketDataPath(symbol)}/update`)).data,

  backfill: async (symbol: string, request: BackfillRequest): Promise<JobSubmission> =>
    (await httpClient.post<JobSubmission>(`${marketDataPath(symbol)}/backfill`, request)).data,

  /** Replaces stored bars in the range; the backend keeps existing data if the provider fails. */
  repopulate: async (symbol: string, request: RepopulateRequest): Promise<JobSubmission> =>
    (await httpClient.post<JobSubmission>(`${marketDataPath(symbol)}/repopulate`, request)).data,

  bulkLoad: async (request: BulkLoadRequest): Promise<BulkLoadResponse> =>
    (await httpClient.post<BulkLoadResponse>('/admin/market-data/bulk-load', request)).data,

  /** Queues an incremental update for every watched, active stock without an active job. */
  synchronize: async (): Promise<SynchronizationResult> =>
    (await httpClient.post<SynchronizationResult>('/admin/market-data/synchronize')).data,
};
