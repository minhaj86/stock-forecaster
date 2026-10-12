import { useQuery } from '@tanstack/react-query';
import { adminDashboardApi } from '@/api/admin/adminDashboardApi';
import { queryKeys } from '@/api/queryKeys';

/** While jobs are queued or running, the overview refreshes itself. */
const POLL_WHILE_JOBS_ACTIVE_MS = 10_000;

export function useAdminDashboard() {
  return useQuery({
    queryKey: queryKeys.admin.dashboard,
    queryFn: adminDashboardApi.get,
    refetchInterval: (query) => {
      const jobs = query.state.data?.jobs;
      return jobs && (jobs.queued > 0 || jobs.running > 0) ? POLL_WHILE_JOBS_ACTIVE_MS : false;
    },
  });
}
