import { useState } from 'react';

/**
 * Keeps showing the last non-null value after it becomes null, so a dialog keeps its content while it animates
 * closed.
 */
export function useRetainedValue<T>(value: T | null): T | null {
  const [retained, setRetained] = useState<T | null>(value);
  if (value !== null && value !== retained) setRetained(value);
  return value ?? retained;
}
