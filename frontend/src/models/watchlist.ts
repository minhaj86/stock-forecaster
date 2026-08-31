import type { AssetType } from './stock';

export interface WatchlistSummary {
  id: string;
  name: string;
  stockCount: number;
  /** Symbols in the order they were added. */
  symbols: string[];
  createdAt: string;
  updatedAt: string;
}

export interface WatchlistStock {
  symbol: string;
  companyName: string;
  exchange: string;
  currency: string;
  sector: string | null;
  assetType: AssetType;
}

export interface WatchlistItem {
  stock: WatchlistStock;
  addedAt: string;
  notes: string | null;
}

export interface Watchlist {
  id: string;
  name: string;
  stockCount: number;
  createdAt: string;
  updatedAt: string;
  items: WatchlistItem[];
}
