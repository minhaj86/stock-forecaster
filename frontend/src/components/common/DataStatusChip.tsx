import Chip from '@mui/material/Chip';
import type { DataStatus } from '@/models/analytics';

const LABELS: Record<Exclude<DataStatus, 'READY'>, { label: string; color: 'info' | 'warning' | 'default' }> = {
  PENDING: { label: 'Syncing…', color: 'info' },
  NOT_TRACKED: { label: 'Not tracked', color: 'default' },
  UNAVAILABLE: { label: 'Unavailable', color: 'warning' },
};

/** Shown instead of metrics when market data is not (yet) available. */
export function DataStatusChip({ status }: { status: DataStatus }) {
  if (status === 'READY') return null;
  const { label, color } = LABELS[status];
  return <Chip size="small" variant="outlined" color={color} label={label} />;
}
