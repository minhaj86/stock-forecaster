import { z } from 'zod';
import type { ExistingDataStrategy, MarketDataInterval } from '@/models/admin';

/**
 * Calendar dates (`yyyy-MM-dd`) as the backend validates them: `fromDate ≥ 1970-01-01` and
 * `fromDate ≤ toDate ≤ today (UTC)`. ISO dates compare correctly as strings.
 */
export const EARLIEST_DATE = '1970-01-01';
export const DEFAULT_HISTORY_YEARS = 5;

export function todayUtc(now: Date = new Date()): string {
  return now.toISOString().slice(0, 10);
}

/** The same calendar day `years` earlier (29 February falls back to 1 March). */
export function yearsBefore(isoDate: string, years: number): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Date(Date.UTC(year - years, month - 1, day)).toISOString().slice(0, 10);
}

export interface DateRange {
  fromDate: string;
  toDate: string;
}

/** The default range for loading history: the last five years up to today. */
export function defaultHistoryRange(today: string): DateRange {
  return { fromDate: yearsBefore(today, DEFAULT_HISTORY_YEARS), toDate: today };
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

const date = (missing: string) => z.string().min(1, { error: missing }).regex(ISO_DATE, { error: 'Use the format yyyy-mm-dd' });

function checkRange(today: string) {
  return (values: DateRange, context: z.RefinementCtx) => {
    const { fromDate, toDate } = values;
    if (ISO_DATE.test(fromDate) && fromDate < EARLIEST_DATE) {
      context.addIssue({ code: 'custom', path: ['fromDate'], message: `Choose ${EARLIEST_DATE} or later` });
    }
    if (ISO_DATE.test(toDate) && toDate > today) {
      context.addIssue({ code: 'custom', path: ['toDate'], message: 'Choose today or an earlier date' });
    }
    if (ISO_DATE.test(fromDate) && ISO_DATE.test(toDate) && fromDate > toDate) {
      context.addIssue({ code: 'custom', path: ['toDate'], message: 'Choose a date on or after the start date' });
    }
  };
}

const interval = z.enum(['ONE_DAY'] as const satisfies readonly MarketDataInterval[]);
const existingDataStrategy = z.enum(['SKIP', 'UPDATE'] as const satisfies readonly ExistingDataStrategy[]);

export function createBackfillSchema(today: string) {
  return z.object({
    fromDate: date('Choose a start date'),
    toDate: date('Choose an end date'),
    interval,
    existingDataStrategy,
  }).superRefine(checkRange(today));
}
export type BackfillFormValues = z.infer<ReturnType<typeof createBackfillSchema>>;

export function createRepopulateSchema(today: string) {
  return z.object({
    fromDate: date('Choose a start date'),
    toDate: date('Choose an end date'),
    interval,
  }).superRefine(checkRange(today));
}
export type RepopulateFormValues = z.infer<ReturnType<typeof createRepopulateSchema>>;

export const MAX_BULK_STOCKS = 50;

export function createBulkLoadSchema(today: string) {
  return z.object({
    stocks: z.array(z.object({ symbol: z.string(), companyName: z.string() }))
      .min(1, { error: 'Choose at least one stock' })
      .max(MAX_BULK_STOCKS, { error: `Choose at most ${MAX_BULK_STOCKS} stocks` }),
    fromDate: date('Choose a start date'),
    toDate: date('Choose an end date'),
    interval,
    existingDataStrategy,
  }).superRefine(checkRange(today));
}
export type BulkLoadFormValues = z.infer<ReturnType<typeof createBulkLoadSchema>>;
