import { describe, expect, it } from 'vitest';
import {
  formatCompact, formatCurrency, formatDate, formatDuration, formatInteger, formatPercent, formatSignedCurrency, greeting,
} from './format';

describe('format', () => {
  it('formats signed percentages from ratios', () => {
    expect(formatPercent(0.0423)).toBe('+4.23%');
    expect(formatPercent(-0.0211)).toBe('-2.11%');
    expect(formatPercent(0)).toBe('0.00%');
    expect(formatPercent(0.327, { signed: false, digits: 1 })).toBe('32.7%');
    expect(formatPercent(null)).toBe('—');
  });

  it('formats money in the instrument currency', () => {
    expect(formatCurrency(1234.5, 'USD')).toBe('$1,234.50');
    expect(formatSignedCurrency(4.22, 'USD')).toBe('+$4.22');
    expect(formatSignedCurrency(-4.22, 'USD')).toBe('-$4.22');
    expect(formatCurrency(undefined)).toBe('—');
  });

  it('keeps calendar dates stable regardless of the viewer time zone', () => {
    expect(formatDate('2026-10-09')).toBe('Oct 9, 2026');
  });

  it('formats large numbers compactly', () => {
    expect(formatCompact(12_500_000)).toBe('12.5M');
  });

  it('formats counts as grouped whole numbers', () => {
    expect(formatInteger(1258)).toBe('1,258');
    expect(formatInteger(0)).toBe('0');
    expect(formatInteger(null)).toBe('—');
  });

  it('formats durations in the largest sensible units', () => {
    expect(formatDuration(850)).toBe('850 ms');
    expect(formatDuration(12_430)).toBe('12.4 s');
    expect(formatDuration(59_970)).toBe('1 min');
    expect(formatDuration(252_000)).toBe('4 min 12 s');
    expect(formatDuration(7_500_000)).toBe('2 h 5 min');
    expect(formatDuration(3_600_000)).toBe('1 h');
    expect(formatDuration(null)).toBe('—');
    expect(formatDuration(-1)).toBe('—');
  });

  it('greets by time of day', () => {
    expect(greeting(new Date(2026, 9, 11, 9))).toBe('Good morning');
    expect(greeting(new Date(2026, 9, 11, 15))).toBe('Good afternoon');
    expect(greeting(new Date(2026, 9, 11, 21))).toBe('Good evening');
  });
});
