import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminJobsApi } from '@/api/admin/adminJobsApi';
import { queryKeys } from '@/api/queryKeys';
import type { MarketDataJobFilters } from '@/models/admin';
import { invalidateMarketData } from '../invalidation';
import { isJobActive } from '../labels';
import { useRefreshWhenJobFinishes } from '../useRefreshWhenJobFinishes';

const POLL_LIST_MS = 5_000;
const POLL_JOB_MS = 2_000;

/** Polls while any visible job is queued or running. */
export function useMarketDataJobs(filters: MarketDataJobFilters) {
  return useQuery({
    queryKey: queryKeys.admin.jobs(filters),
    queryFn: () => adminJobsApi.list(filters),
    placeholderData: keepPreviousData,
    refetchInterval: (query) =>
      (query.state.data?.content.some((job) => isJobActive(job.status)) ? POLL_LIST_MS : false),
  });
}

/** Polls while the job is queued or running; its completion refreshes everything derived from market data. */
export function useMarketDataJob(jobId: string) {
  const query = useQuery({
    queryKey: queryKeys.admin.job(jobId),
    queryFn: () => adminJobsApi.get(jobId),
    enabled: jobId.length > 0,
    refetchInterval: (current) => (current.state.data && isJobActive(current.state.data.status) ? POLL_JOB_MS : false),
  });
  useRefreshWhenJobFinishes(query.data ? isJobActive(query.data.status) : false);
  return query;
}

export function useRetryJob() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (jobId: string) => adminJobsApi.retry(jobId),
    onSuccess: (submission) => {
      queryClient.setQueryData(queryKeys.admin.job(submission.job.id), submission.job);
      return invalidateMarketData(queryClient);
    },
  });
}

export function useCancelJob() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (jobId: string) => adminJobsApi.cancel(jobId),
    onSuccess: (job) => {
      queryClient.setQueryData(queryKeys.admin.job(job.id), job);
      return invalidateMarketData(queryClient);
    },
  });
}
