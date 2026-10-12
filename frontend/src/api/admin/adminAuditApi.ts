import type { AuditEvent, AuditLogFilters } from '@/models/admin';
import type { Page } from '@/models/api';
import { httpClient } from '../httpClient';
import { compactParams } from './params';

export const adminAuditApi = {
  /** Newest first. */
  list: async (filters: AuditLogFilters): Promise<Page<AuditEvent>> =>
    (await httpClient.get<Page<AuditEvent>>('/admin/audit-log', { params: compactParams({ ...filters }) })).data,
};
