import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '@/api/apiError';

/** Client errors (4xx) are not retried: repeating the same request cannot succeed. */
function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false;
  return failureCount < 2;
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        gcTime: 5 * 60_000,
        retry: shouldRetry,
        refetchOnWindowFocus: true,
      },
      mutations: { retry: false },
    },
  });
}
