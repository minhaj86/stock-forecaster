import { describe, expect, it } from 'vitest';
import { jobErrorLabel } from './labels';

describe('jobErrorLabel', () => {
  it('labels the known error codes', () => {
    expect(jobErrorLabel('PROVIDER_REJECTED_REQUEST')).toBe('The provider rejected the request');
    expect(jobErrorLabel('PROVIDER_RATE_LIMITED')).toBe('Provider rate limit reached');
  });

  it('leaves codes it does not know to be shown as sent', () => {
    expect(jobErrorLabel('PROVIDER_QUOTA_EXHAUSTED')).toBeUndefined();
    expect(jobErrorLabel('constructor')).toBeUndefined();
    expect(jobErrorLabel('toString')).toBeUndefined();
  });
});
