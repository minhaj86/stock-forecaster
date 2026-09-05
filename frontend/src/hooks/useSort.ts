import { useMemo, useState } from 'react';

export type SortDirection = 'asc' | 'desc';

export interface SortState<K extends string> {
  key: K;
  direction: SortDirection;
}

/**
 * Client-side sorting for small tables. Missing values (null/undefined) always sort last.
 */
export function useSort<T, K extends string>(rows: T[], accessors: Record<K, (row: T) => string | number | null | undefined>,
  initial: SortState<K>) {
  const [sort, setSort] = useState<SortState<K>>(initial);

  const sorted = useMemo(() => {
    const accessor = accessors[sort.key];
    const factor = sort.direction === 'asc' ? 1 : -1;
    return [...rows].sort((a, b) => {
      const left = accessor(a);
      const right = accessor(b);
      if (left === null || left === undefined) return right === null || right === undefined ? 0 : 1;
      if (right === null || right === undefined) return -1;
      if (typeof left === 'string' && typeof right === 'string') return left.localeCompare(right) * factor;
      return ((left as number) - (right as number)) * factor;
    });
  }, [rows, accessors, sort]);

  /** Re-clicking a column flips its direction; a new column starts with {@code firstDirection}. */
  const toggle = (key: K, firstDirection: SortDirection = 'desc') =>
    setSort((current) =>
      current.key === key
        ? { key, direction: current.direction === 'asc' ? 'desc' : 'asc' }
        : { key, direction: firstDirection });

  return { sorted, sort, toggle };
}
