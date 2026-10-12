import type { SynchronizationResult } from '@/models/admin';
import { formatInteger } from '@/utils/format';

const plural = (count: number, singular: string, pluralForm = `${singular}s`) =>
  `${formatInteger(count)} ${count === 1 ? singular : pluralForm}`;

/** Feedback after "Trigger synchronization": how many updates were queued for how many watched stocks. */
export function synchronizationMessage({ stocksConsidered, jobsQueued }: SynchronizationResult): string {
  if (stocksConsidered === 0) return 'No watched stocks to synchronize.';
  if (jobsQueued === 0) {
    return `No updates queued: all ${plural(stocksConsidered, 'watched stock')} already have a queued or running job.`;
  }
  return `Queued ${plural(jobsQueued, 'update')} for ${plural(stocksConsidered, 'watched stock')}`;
}
