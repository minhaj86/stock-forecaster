import AdminPanelSettingsOutlinedIcon from '@mui/icons-material/AdminPanelSettingsOutlined';
import BlockIcon from '@mui/icons-material/Block';
import BuildCircleOutlinedIcon from '@mui/icons-material/BuildCircleOutlined';
import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlined';
import HelpOutlineIcon from '@mui/icons-material/HelpOutlined';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import PersonOutlineIcon from '@mui/icons-material/PersonOutlined';
import RemoveCircleOutlineIcon from '@mui/icons-material/RemoveCircleOutlined';
import ScheduleIcon from '@mui/icons-material/Schedule';
import SyncIcon from '@mui/icons-material/Sync';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import Chip from '@mui/material/Chip';
import type { ReactElement } from 'react';
import type { AccountStatus, MarketDataJobStatus, MarketDataStatus, ProviderHealthStatus } from '@/models/admin';
import type { Role } from '@/models/auth';
import {
  ACCOUNT_STATUS_LABELS, JOB_STATUS_LABELS, MARKET_DATA_STATUS_LABELS, PROVIDER_STATUS_LABELS, ROLE_LABELS,
} from '../labels';

type Tone = 'success' | 'info' | 'warning' | 'error' | 'default';

/**
 * Status shown with an icon and text, never colour alone. The text keeps the primary text colour for contrast in both
 * modes; the tone colours only the icon and the outline.
 */
function StatusChip({ label, icon, tone }: { label: string; icon: ReactElement; tone: Tone }) {
  const color = tone === 'default' ? 'text.secondary' : `${tone}.main`;
  return (
    <Chip
      size="small"
      variant="outlined"
      icon={icon}
      label={label}
      sx={{
        fontWeight: 600,
        color: 'text.primary',
        borderColor: tone === 'default' ? 'divider' : color,
        '& .MuiChip-icon': { color },
      }}
    />
  );
}

const ACCOUNT_STATUS: Record<AccountStatus, { icon: ReactElement; tone: Tone }> = {
  ACTIVE: { icon: <CheckCircleOutlineIcon />, tone: 'success' },
  DISABLED: { icon: <BlockIcon />, tone: 'default' },
  LOCKED: { icon: <LockOutlinedIcon />, tone: 'warning' },
};

export function AccountStatusChip({ status }: { status: AccountStatus }) {
  const { icon, tone } = ACCOUNT_STATUS[status];
  return <StatusChip label={ACCOUNT_STATUS_LABELS[status]} icon={icon} tone={tone} />;
}

export function RoleChip({ role }: { role: Role }) {
  return role === 'ADMIN'
    ? <StatusChip label={ROLE_LABELS.ADMIN} icon={<AdminPanelSettingsOutlinedIcon />} tone="info" />
    : <StatusChip label={ROLE_LABELS.USER} icon={<PersonOutlineIcon />} tone="default" />;
}

export function StockActiveChip({ active }: { active: boolean }) {
  return active
    ? <StatusChip label="Active" icon={<CheckCircleOutlineIcon />} tone="success" />
    : <StatusChip label="Inactive" icon={<VisibilityOffOutlinedIcon />} tone="default" />;
}

const MARKET_DATA_STATUS: Record<MarketDataStatus, { icon: ReactElement; tone: Tone }> = {
  READY: { icon: <CheckCircleOutlineIcon />, tone: 'success' },
  PENDING: { icon: <SyncIcon />, tone: 'info' },
  FAILED: { icon: <ErrorOutlineIcon />, tone: 'error' },
  NOT_TRACKED: { icon: <RemoveCircleOutlineIcon />, tone: 'default' },
};

export function MarketDataStatusChip({ status }: { status: MarketDataStatus }) {
  const { icon, tone } = MARKET_DATA_STATUS[status];
  return <StatusChip label={MARKET_DATA_STATUS_LABELS[status]} icon={icon} tone={tone} />;
}

const JOB_STATUS: Record<MarketDataJobStatus, { icon: ReactElement; tone: Tone }> = {
  QUEUED: { icon: <ScheduleIcon />, tone: 'default' },
  RUNNING: { icon: <SyncIcon />, tone: 'info' },
  COMPLETED: { icon: <CheckCircleOutlineIcon />, tone: 'success' },
  PARTIALLY_COMPLETED: { icon: <WarningAmberIcon />, tone: 'warning' },
  FAILED: { icon: <ErrorOutlineIcon />, tone: 'error' },
  CANCELLED: { icon: <CancelOutlinedIcon />, tone: 'default' },
};

export function JobStatusChip({ status }: { status: MarketDataJobStatus }) {
  const { icon, tone } = JOB_STATUS[status];
  return <StatusChip label={JOB_STATUS_LABELS[status]} icon={icon} tone={tone} />;
}

const PROVIDER_STATUS: Record<ProviderHealthStatus, { icon: ReactElement; tone: Tone }> = {
  HEALTHY: { icon: <CheckCircleOutlineIcon />, tone: 'success' },
  DEGRADED: { icon: <WarningAmberIcon />, tone: 'warning' },
  UNAVAILABLE: { icon: <ErrorOutlineIcon />, tone: 'error' },
  MISCONFIGURED: { icon: <BuildCircleOutlinedIcon />, tone: 'error' },
  UNKNOWN: { icon: <HelpOutlineIcon />, tone: 'default' },
};

export function ProviderStatusChip({ status }: { status: ProviderHealthStatus }) {
  const { icon, tone } = PROVIDER_STATUS[status];
  return <StatusChip label={PROVIDER_STATUS_LABELS[status]} icon={icon} tone={tone} />;
}
