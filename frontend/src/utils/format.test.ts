import { describe, expect, it } from 'vitest';
import { formatCompact, formatCurrency, formatDate, formatPercent, formatSignedCurrency, greeting } from './format';

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

  it('greets by time of day', () => {
    expect(greeting(new Date(2026, 9, 11, 9))).toBe('Good morning');
    expect(greeting(new Date(2026, 9, 11, 15))).toBe('Good afternoon');
    expect(greeting(new Date(2026, 9, 11, 21))).toBe('Good evening');
  });
});
