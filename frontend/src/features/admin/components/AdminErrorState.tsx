import type { ComponentProps } from 'react';
import { ErrorState } from '@/components/common/ErrorState';
import { friendlyError } from '../errors';

/** `ErrorState` with clearer messages for market-data provider failures (unavailable, rate limited). */
export function AdminErrorState({ error, ...props }: ComponentProps<typeof ErrorState>) {
  return <ErrorState {...props} error={friendlyError(error)} />;
}
