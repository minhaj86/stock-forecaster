import { describe, expect, it } from 'vitest';
import { createBackfillSchema, defaultHistoryRange, todayUtc, yearsBefore } from './dateRange';

describe('date range helpers', () => {
  it('uses the UTC calendar day', () => {
    expect(todayUtc(new Date('2026-10-11T23:30:00Z'))).toBe('2026-10-11');
    expect(todayUtc(new Date('2026-10-11T22:30:00-02:00'))).toBe('2026-10-12');
  });

  it('goes back whole years', () => {
    expect(yearsBefore('2026-10-11', 5)).toBe('2021-10-11');
    expect(yearsBefore('2028-02-29', 1)).toBe('2027-03-01');
    expect(defaultHistoryRange('2026-10-11')).toEqual({ fromDate: '2021-10-11', toDate: '2026-10-11' });
  });
});

describe('createBackfillSchema', () => {
  const schema = createBackfillSchema('2026-10-11');
  const valid = { fromDate: '2015-01-01', toDate: '2026-10-09', interval: 'ONE_DAY', existingDataStrategy: 'SKIP' };
  const issues = (values: Record<string, string>) => {
    const result = schema.safeParse(values);
    return result.success ? [] : result.error.issues.map((issue) => `${issue.path.join('.')}: ${issue.message}`);
  };

  it('accepts a range up to today', () => {
    expect(issues(valid)).toEqual([]);
    expect(issues({ ...valid, toDate: '2026-10-11', fromDate: '2026-10-11' })).toEqual([]);
  });

  it('mirrors the backend date rules', () => {
    expect(issues({ ...valid, toDate: '2026-10-12' })).toEqual(['toDate: Choose today or an earlier date']);
    expect(issues({ ...valid, fromDate: '2026-10-10', toDate: '2026-10-09' }))
      .toEqual(['toDate: Choose a date on or after the start date']);
    expect(issues({ ...valid, fromDate: '1969-12-31' })).toEqual(['fromDate: Choose 1970-01-01 or later']);
    expect(issues({ ...valid, fromDate: '' })).toContain('fromDate: Choose a start date');
  });
});
