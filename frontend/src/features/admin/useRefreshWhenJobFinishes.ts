import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { invalidateMarketData } from './invalidation';

/**
 * When a job the view is following finishes, stored market data has changed: refetch the admin views and every
 * user-facing view derived from prices (dashboard, analytics, price history, forecasts).
 */
export function useRefreshWhenJobFinishes(active: boolean) {
  const queryClient = useQueryClient();
  const wasActive = useRef(active);
  useEffect(() => {
    if (wasActive.current && !active) void invalidateMarketData(queryClient);
    wasActive.current = active;
  }, [active, queryClient]);
}
