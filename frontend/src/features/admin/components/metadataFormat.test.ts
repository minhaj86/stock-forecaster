import { describe, expect, it } from 'vitest';
import { formatJsonValue, humanizeKey } from './metadataFormat';

describe('metadata formatting', () => {
  it('turns keys into labels', () => {
    expect(humanizeKey('planCategory')).toBe('Plan category');
    expect(humanizeKey('minute_limit')).toBe('Minute limit');
    expect(humanizeKey('newStatus')).toBe('New status');
  });

  it('formats scalar and nested values', () => {
    expect(formatJsonValue('ACTIVE')).toBe('ACTIVE');
    expect(formatJsonValue(800)).toBe('800');
    expect(formatJsonValue(12_345)).toBe('12,345');
    expect(formatJsonValue(true)).toBe('Yes');
    expect(formatJsonValue(null)).toBe('—');
    expect(formatJsonValue({ from: '2015-01-01' })).toBe('{\n  "from": "2015-01-01"\n}');
  });
});
