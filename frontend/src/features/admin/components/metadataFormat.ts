import type { JsonValue } from '@/models/admin';
import { formatInteger, formatNumber } from '@/utils/format';

/** "planCategory" → "Plan category", "minute_limit" → "Minute limit". */
export function humanizeKey(key: string): string {
  const words = key.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ').trim().toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** Scalar values as text; nested objects and arrays as indented JSON. */
export function formatJsonValue(value: JsonValue): string {
  if (value === null) return '—';
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (typeof value === 'number') return Number.isInteger(value) ? formatInteger(value) : formatNumber(value);
  if (typeof value === 'string') return value;
  return JSON.stringify(value, null, 2);
}

export const isStructured = (value: JsonValue) => value !== null && typeof value === 'object';
