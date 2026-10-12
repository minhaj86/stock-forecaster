import type {
  AdminStock, AdminStockFilters, CreateStockRequest, CreateStockResponse, ProviderSearchResponse,
} from '@/models/admin';
import type { Page } from '@/models/api';
import { httpClient } from '../httpClient';
import { compactParams } from './params';

export const adminStockPath = (symbol: string) => `/admin/stocks/${encodeURIComponent(symbol)}`;

export const adminStocksApi = {
  list: async (filters: AdminStockFilters, signal?: AbortSignal): Promise<Page<AdminStock>> =>
    (await httpClient.get<Page<AdminStock>>('/admin/stocks', { params: compactParams({ ...filters }), signal })).data,

  /** Also returns inactive stocks. */
  get: async (symbol: string): Promise<AdminStock> => (await httpClient.get<AdminStock>(adminStockPath(symbol))).data,

  /** Searches the market-data provider's instrument list (not the catalogue). */
  providerSearch: async (query: string, signal?: AbortSignal): Promise<ProviderSearchResponse> =>
    (await httpClient.get<ProviderSearchResponse>('/admin/stocks/provider-search', { params: { query }, signal })).data,

  create: async (request: CreateStockRequest): Promise<CreateStockResponse> =>
    (await httpClient.post<CreateStockResponse>('/admin/stocks', request)).data,

  activate: async (symbol: string): Promise<AdminStock> =>
    (await httpClient.post<AdminStock>(`${adminStockPath(symbol)}/activate`)).data,

  deactivate: async (symbol: string): Promise<AdminStock> =>
    (await httpClient.post<AdminStock>(`${adminStockPath(symbol)}/deactivate`)).data,
};
