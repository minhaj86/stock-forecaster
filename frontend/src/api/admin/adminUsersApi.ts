import type {
  AdminUser, AdminUserDetails, AdminUserFilters, CreateUserRequest, ResetPasswordRequest, UpdateUserRequest,
  UserStatistics,
} from '@/models/admin';
import type { Page } from '@/models/api';
import { httpClient } from '../httpClient';
import { compactParams } from './params';

const path = (id: string) => `/admin/users/${encodeURIComponent(id)}`;

export const adminUsersApi = {
  list: async (filters: AdminUserFilters): Promise<Page<AdminUser>> =>
    (await httpClient.get<Page<AdminUser>>('/admin/users', { params: compactParams({ ...filters }) })).data,

  statistics: async (): Promise<UserStatistics> =>
    (await httpClient.get<UserStatistics>('/admin/users/statistics')).data,

  get: async (id: string): Promise<AdminUserDetails> => (await httpClient.get<AdminUserDetails>(path(id))).data,

  create: async (request: CreateUserRequest): Promise<AdminUserDetails> =>
    (await httpClient.post<AdminUserDetails>('/admin/users', request)).data,

  update: async (id: string, request: UpdateUserRequest): Promise<AdminUserDetails> =>
    (await httpClient.patch<AdminUserDetails>(path(id), request)).data,

  disable: async (id: string): Promise<AdminUserDetails> =>
    (await httpClient.post<AdminUserDetails>(`${path(id)}/disable`)).data,

  enable: async (id: string): Promise<AdminUserDetails> =>
    (await httpClient.post<AdminUserDetails>(`${path(id)}/enable`)).data,

  /** Sets a temporary password; the backend signs the user out everywhere. */
  resetPassword: async (id: string, request: ResetPasswordRequest): Promise<AdminUserDetails> =>
    (await httpClient.post<AdminUserDetails>(`${path(id)}/password-reset`, request)).data,

  remove: async (id: string): Promise<void> => {
    await httpClient.delete(path(id));
  },
};
