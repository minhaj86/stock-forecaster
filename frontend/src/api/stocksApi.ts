import type { Page } from '@/models/api';
import type { Stock } from '@/models/stock';
import { httpClient } from './httpClient';

export const stocksApi = {
  search: async (query: string, limit = 8, signal?: AbortSignal): Promise<Stock[]> =>
    (await httpClient.get<Stock[]>('/stocks/search', { params: { q: query, limit }, signal })).data,

  browse: async (sector: string | null, page: number, size = 20): Promise<Page<Stock>> =>
    (await httpClient.get<Page<Stock>>('/stocks', { params: { sector: sector ?? undefined, page, size } })).data,

  sectors: async (): Promise<string[]> => (await httpClient.get<string[]>('/stocks/sectors')).data,

  get: async (symbol: string): Promise<Stock> =>
    (await httpClient.get<Stock>(`/stocks/${encodeURIComponent(symbol)}`)).data,
};
