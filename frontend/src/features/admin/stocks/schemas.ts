import { z } from 'zod';
import type { ManualStockDetails } from '@/models/admin';
import type { AssetType } from '@/models/stock';

/** Mirrors the backend rules for manually entered stocks. Codes are sent upper-case. */
export const manualStockSchema = z.object({
  symbol: z.string().trim()
    .min(1, { error: 'Enter the ticker symbol' })
    .regex(/^[A-Za-z0-9][A-Za-z0-9.-]{0,19}$/, { error: 'Use up to 20 letters, digits, dots or hyphens, starting with a letter or digit' }),
  companyName: z.string().trim()
    .min(1, { error: 'Enter the company name' })
    .max(200, { error: 'Use at most 200 characters' }),
  exchange: z.string().trim()
    .min(1, { error: 'Enter the exchange' })
    .max(40, { error: 'Use at most 40 characters' }),
  mic: z.string().trim().regex(/^[A-Za-z0-9]{4}$/, { error: 'Enter the 4-character market identifier code, e.g. XNAS' }),
  currency: z.string().trim().regex(/^[A-Za-z]{3}$/, { error: 'Enter a 3-letter currency code, e.g. USD' }),
  country: z.string().trim().regex(/^[A-Za-z]{2}$/, { error: 'Enter a 2-letter country code, e.g. US' }),
  sector: z.string().trim().max(80, { error: 'Use at most 80 characters' }),
  industry: z.string().trim().max(120, { error: 'Use at most 120 characters' }),
  assetType: z.enum(['COMMON_STOCK', 'ADR', 'ETF'] as const satisfies readonly AssetType[]),
});
export type ManualStockFormValues = z.infer<typeof manualStockSchema>;

export const EMPTY_MANUAL_STOCK: ManualStockFormValues = {
  symbol: '', companyName: '', exchange: '', mic: '', currency: '', country: '', sector: '', industry: '',
  assetType: 'COMMON_STOCK',
};

/** The request body of a manual stock: upper-case codes, empty optional fields as `null`. */
export function toManualRequest(values: ManualStockFormValues): { symbol: string; manual: ManualStockDetails } {
  return {
    symbol: values.symbol.toUpperCase(),
    manual: {
      companyName: values.companyName,
      exchange: values.exchange,
      mic: values.mic.toUpperCase(),
      currency: values.currency.toUpperCase(),
      country: values.country.toUpperCase(),
      sector: values.sector || null,
      industry: values.industry || null,
      assetType: values.assetType,
    },
  };
}
