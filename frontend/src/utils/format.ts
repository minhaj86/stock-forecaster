/**
 * Locale-aware formatting for financial values. Ratios from the backend are decimal fractions (0.052 = 5.2 %).
 */
const locale = (): string | undefined => (typeof navigator === 'undefined' ? undefined : navigator.language);

const PLACEHOLDER = '—';

export function formatCurrency(value: number | null | undefined, currency = 'USD'): string {
  if (value === null || value === undefined || Number.isNaN(value)) return PLACEHOLDER;
  return new Intl.NumberFormat(locale(), { style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2 })
    .format(value);
}

export function formatSignedCurrency(value: number | null | undefined, currency = 'USD'): string {
  if (value === null || value === undefined || Number.isNaN(value)) return PLACEHOLDER;
  return new Intl.NumberFormat(locale(), {
    style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2, signDisplay: 'exceptZero',
  }).format(value);
}

export function formatPercent(ratio: number | null | undefined, options: { signed?: boolean; digits?: number } = {}): string {
  if (ratio === null || ratio === undefined || Number.isNaN(ratio)) return PLACEHOLDER;
  const { signed = true, digits = 2 } = options;
  return new Intl.NumberFormat(locale(), {
    style: 'percent', minimumFractionDigits: digits, maximumFractionDigits: digits,
    signDisplay: signed ? 'exceptZero' : 'auto',
  }).format(ratio);
}

export function formatNumber(value: number | null | undefined, digits = 2): string {
  if (value === null || value === undefined || Number.isNaN(value)) return PLACEHOLDER;
  return new Intl.NumberFormat(locale(), { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(value);
}

/** Whole numbers with grouping, e.g. record counts ("1,258"). */
export function formatInteger(value: number | null | undefined): string {
  return formatNumber(value, 0);
}

/** Elapsed time in the largest sensible units: "850 ms", "12.4 s", "4 min 12 s", "2 h 5 min". */
export function formatDuration(milliseconds: number | null | undefined): string {
  if (milliseconds === null || milliseconds === undefined || Number.isNaN(milliseconds) || milliseconds < 0) {
    return PLACEHOLDER;
  }
  if (milliseconds < 1000) return `${Math.round(milliseconds)} ms`;
  const tenthsOfSeconds = Math.round(milliseconds / 100) / 10;
  if (tenthsOfSeconds < 60) return `${formatNumber(tenthsOfSeconds, 1)} s`;
  const totalSeconds = Math.round(milliseconds / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) return minutes > 0 ? `${hours} h ${minutes} min` : `${hours} h`;
  return seconds > 0 ? `${minutes} min ${seconds} s` : `${minutes} min`;
}

export function formatCompact(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return PLACEHOLDER;
  return new Intl.NumberFormat(locale(), { notation: 'compact', maximumFractionDigits: 1 }).format(value);
}

/** Formats a calendar date ("2026-10-09") without shifting it into the viewer's time zone. */
export function formatDate(isoDate: string | null | undefined, style: 'medium' | 'long' = 'medium'): string {
  if (!isoDate) return PLACEHOLDER;
  const date = new Date(`${isoDate.slice(0, 10)}T00:00:00Z`);
  return new Intl.DateTimeFormat(locale(), { dateStyle: style, timeZone: 'UTC' }).format(date);
}

export function formatTime(instant: string | null | undefined): string {
  if (!instant) return PLACEHOLDER;
  return new Intl.DateTimeFormat(locale(), { timeStyle: 'short' }).format(new Date(instant));
}

export function formatDateTime(instant: string | null | undefined): string {
  if (!instant) return PLACEHOLDER;
  return new Intl.DateTimeFormat(locale(), { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(instant));
}

export function greeting(now: Date = new Date()): string {
  const hour = now.getHours();
  if (hour < 5) return 'Good evening';
  if (hour < 12) return 'Good morning';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
}
