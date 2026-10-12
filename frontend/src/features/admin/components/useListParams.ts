import { useCallback } from 'react';
import { useSearchParams } from 'react-router';

export const PAGE_SIZES = [10, 20, 50, 100] as const;
export const DEFAULT_PAGE_SIZE = 20;

/**
 * Filters and pagination of an admin list kept in the URL, so links can target a filtered view
 * (`/app/admin/sync-jobs?status=FAILED`) and returning from a details page restores the list. `page` is 1-based in the
 * URL and 0-based here (as in the API).
 */
export function useListParams() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, (Number.parseInt(params.get('page') ?? '', 10) || 1) - 1);
  const requestedSize = Number.parseInt(params.get('size') ?? '', 10);
  const size = (PAGE_SIZES as readonly number[]).includes(requestedSize) ? requestedSize : DEFAULT_PAGE_SIZE;

  /** Changing a filter starts again on the first page; empty values remove the parameter. */
  const setFilters = useCallback((changes: Record<string, string | null | undefined>) => {
    setParams((current) => {
      const next = new URLSearchParams(current);
      for (const [key, value] of Object.entries(changes)) {
        if (value) next.set(key, value);
        else next.delete(key);
      }
      next.delete('page');
      return next;
    }, { replace: true });
  }, [setParams]);

  const setPage = useCallback((nextPage: number) => {
    setParams((current) => {
      const next = new URLSearchParams(current);
      if (nextPage > 0) next.set('page', String(nextPage + 1));
      else next.delete('page');
      return next;
    }, { replace: true });
  }, [setParams]);

  const setSize = useCallback((nextSize: number) => {
    setParams((current) => {
      const next = new URLSearchParams(current);
      if (nextSize === DEFAULT_PAGE_SIZE) next.delete('size');
      else next.set('size', String(nextSize));
      next.delete('page');
      return next;
    }, { replace: true });
  }, [setParams]);

  const get = useCallback((key: string) => params.get(key) ?? '', [params]);

  return { params, get, page, size, setFilters, setPage, setSize };
}
