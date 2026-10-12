import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { adminAuditApi } from '@/api/admin/adminAuditApi';
import { queryKeys } from '@/api/queryKeys';
import type { AuditLogFilters } from '@/models/admin';

export function useAuditLog(filters: AuditLogFilters, options: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: queryKeys.admin.audit(filters),
    queryFn: () => adminAuditApi.list(filters),
    placeholderData: keepPreviousData,
    enabled: options.enabled ?? true,
  });
}
