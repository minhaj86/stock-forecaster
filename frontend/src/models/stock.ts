export type AssetType = 'COMMON_STOCK' | 'ADR' | 'ETF';

export interface Stock {
  symbol: string;
  companyName: string;
  exchange: string;
  mic: string;
  currency: string;
  country: string;
  sector: string | null;
  industry: string | null;
  assetType: AssetType;
}
