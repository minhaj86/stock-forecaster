import type { MarketDataJob } from '@/models/admin';
import { formatInteger } from '@/utils/format';

/** "1,258 fetched · 1,250 inserted · 0 updated · 8 skipped"; zero skipped/failed/deleted counts are left out. */
export function recordsSummary(job: MarketDataJob): string {
  if (job.status === 'QUEUED' && job.recordsFetched === 0) return '—';
  const parts = [
    `${formatInteger(job.recordsFetched)} fetched`,
    `${formatInteger(job.recordsInserted)} inserted`,
    `${formatInteger(job.recordsUpdated)} updated`,
  ];
  if (job.recordsSkipped > 0) parts.push(`${formatInteger(job.recordsSkipped)} skipped`);
  if (job.recordsFailed > 0) parts.push(`${formatInteger(job.recordsFailed)} failed`);
  if (job.recordsDeleted > 0) parts.push(`${formatInteger(job.recordsDeleted)} deleted`);
  return parts.join(' · ');
}
