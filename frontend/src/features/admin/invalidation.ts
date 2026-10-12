import type { QueryClient } from '@tanstack/react-query';
import { queryKeys } from '@/api/queryKeys';

/** Every admin mutation writes an audit event and changes the overview figures. */
function invalidateAdminSummary(queryClient: QueryClient) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: queryKeys.admin.dashboard }),
    queryClient.invalidateQueries({ queryKey: queryKeys.admin.auditAll }),
  ]);
}

/** User changes: lists, details and statistics of users, plus the overview and the audit log. */
export function invalidateAdminUsers(queryClient: QueryClient) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: queryKeys.admin.usersAll }),
    invalidateAdminSummary(queryClient),
  ]);
}

/** Catalogue changes (adding, activating, deactivating) also change what users can find. */
export function invalidateAdminStocks(queryClient: QueryClient) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: queryKeys.admin.stocksAll }),
    queryClient.invalidateQueries({ queryKey: queryKeys.stocksAll }),
    invalidateAdminSummary(queryClient),
  ]);
}

/**
 * Market-data jobs change stored prices, so the admin views of stocks and jobs and every user-facing view derived from
 * prices (dashboard, analytics, price history, forecasts) are refetched.
 */
export function invalidateMarketData(queryClient: QueryClient) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: queryKeys.admin.stocksAll }),
    queryClient.invalidateQueries({ queryKey: queryKeys.admin.jobsAll }),
    queryClient.invalidateQueries({ queryKey: queryKeys.dashboardAll }),
    queryClient.invalidateQueries({ queryKey: queryKeys.analyticsAll }),
    queryClient.invalidateQueries({ queryKey: queryKeys.pricesAll }),
    queryClient.invalidateQueries({ queryKey: queryKeys.forecastAll }),
    invalidateAdminSummary(queryClient),
  ]);
}
