import type { Watchlist, WatchlistItem, WatchlistSummary } from '@/models/watchlist';
import { httpClient } from './httpClient';

const path = (id: string) => `/watchlists/${encodeURIComponent(id)}`;

export const watchlistApi = {
  list: async (): Promise<WatchlistSummary[]> => (await httpClient.get<WatchlistSummary[]>('/watchlists')).data,

  get: async (id: string): Promise<Watchlist> => (await httpClient.get<Watchlist>(path(id))).data,

  create: async (name: string): Promise<Watchlist> => (await httpClient.post<Watchlist>('/watchlists', { name })).data,

  rename: async (id: string, name: string): Promise<Watchlist> =>
    (await httpClient.patch<Watchlist>(path(id), { name })).data,

  remove: async (id: string): Promise<void> => {
    await httpClient.delete(path(id));
  },

  addStock: async (id: string, symbol: string, notes?: string): Promise<WatchlistItem> =>
    (await httpClient.post<WatchlistItem>(`${path(id)}/items`, { symbol, notes })).data,

  removeStock: async (id: string, symbol: string): Promise<void> => {
    await httpClient.delete(`${path(id)}/items/${encodeURIComponent(symbol)}`);
  },
};
