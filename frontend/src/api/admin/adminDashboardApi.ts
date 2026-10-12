import type { AdminDashboard } from '@/models/admin';
import { httpClient } from '../httpClient';

export const adminDashboardApi = {
  get: async (): Promise<AdminDashboard> => (await httpClient.get<AdminDashboard>('/admin/dashboard')).data,
};
