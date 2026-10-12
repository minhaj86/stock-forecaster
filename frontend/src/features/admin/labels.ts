import type {
  AccountStatus, AuditAction, AuditEntityType, ExistingDataStrategy, MarketDataInterval, MarketDataJobErrorCode,
  MarketDataJobStatus, MarketDataJobTrigger, MarketDataJobType, MarketDataStatus, ProviderHealthStatus,
} from '@/models/admin';
import type { Role } from '@/models/auth';
import type { AssetType } from '@/models/stock';

/** Human-readable labels for the admin API's enums. The arrays keep select options in a deliberate order. */

export const ROLE_LABELS: Record<Role, string> = { USER: 'User', ADMIN: 'Administrator' };
export const ROLES: Role[] = ['USER', 'ADMIN'];

export const ACCOUNT_STATUS_LABELS: Record<AccountStatus, string> = { ACTIVE: 'Active', DISABLED: 'Disabled', LOCKED: 'Locked' };
export const ACCOUNT_STATUSES: AccountStatus[] = ['ACTIVE', 'DISABLED', 'LOCKED'];

export const ASSET_TYPE_LABELS: Record<AssetType, string> = { COMMON_STOCK: 'Common stock', ADR: 'ADR', ETF: 'ETF' };
export const ASSET_TYPES: AssetType[] = ['COMMON_STOCK', 'ADR', 'ETF'];

export const MARKET_DATA_STATUS_LABELS: Record<MarketDataStatus, string> = {
  READY: 'Data ready',
  PENDING: 'Syncing',
  FAILED: 'Sync failed',
  NOT_TRACKED: 'Not tracked',
};

export const PROVIDER_STATUS_LABELS: Record<ProviderHealthStatus, string> = {
  HEALTHY: 'Healthy',
  DEGRADED: 'Degraded',
  UNAVAILABLE: 'Unavailable',
  MISCONFIGURED: 'Misconfigured',
  UNKNOWN: 'Unknown',
};

export const JOB_TYPE_LABELS: Record<MarketDataJobType, string> = {
  INITIAL_LOAD: 'Initial load',
  LATEST_UPDATE: 'Latest-data update',
  BACKFILL: 'Backfill',
  REPOPULATE: 'Re-population',
  AUTOMATIC_SYNC: 'Automatic sync',
};
export const JOB_TYPES: MarketDataJobType[] = ['INITIAL_LOAD', 'LATEST_UPDATE', 'BACKFILL', 'REPOPULATE', 'AUTOMATIC_SYNC'];

export const JOB_STATUS_LABELS: Record<MarketDataJobStatus, string> = {
  QUEUED: 'Queued',
  RUNNING: 'Running',
  COMPLETED: 'Completed',
  PARTIALLY_COMPLETED: 'Partially completed',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
};
export const JOB_STATUSES: MarketDataJobStatus[] = ['QUEUED', 'RUNNING', 'COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED', 'CANCELLED'];

/** Jobs in these states are still being worked on, so views showing them poll. */
export const isJobActive = (status: MarketDataJobStatus) => status === 'QUEUED' || status === 'RUNNING';

export const JOB_TRIGGER_LABELS: Record<MarketDataJobTrigger, string> = {
  ADMIN: 'Administrator',
  WATCHLIST: 'Watchlist',
  SCHEDULE: 'Schedule',
};
export const JOB_TRIGGERS: MarketDataJobTrigger[] = ['ADMIN', 'WATCHLIST', 'SCHEDULE'];

export const INTERVAL_LABELS: Record<MarketDataInterval, string> = { ONE_DAY: 'Daily' };

export const EXISTING_DATA_LABELS: Record<ExistingDataStrategy, string> = {
  SKIP: 'Skip existing records',
  UPDATE: 'Update existing records',
};

export const JOB_ERROR_LABELS: Record<MarketDataJobErrorCode, string> = {
  PROVIDER_TIMEOUT: 'The provider timed out',
  PROVIDER_RATE_LIMITED: 'Provider rate limit reached',
  PROVIDER_UNAVAILABLE: 'The provider is unavailable',
  PROVIDER_AUTHENTICATION_FAILED: 'The provider rejected the API key',
  PROVIDER_PLAN_RESTRICTED: 'Not included in the provider plan',
  PROVIDER_NOT_CONFIGURED: 'The provider is not configured',
  PROVIDER_REJECTED_REQUEST: 'The provider rejected the request',
  SYMBOL_NOT_FOUND: 'Symbol not found at the provider',
  INVALID_PROVIDER_RESPONSE: 'Invalid provider response',
  INVALID_PROVIDER_DATA: 'Invalid provider data',
  UNSUPPORTED_INTERVAL: 'Unsupported interval',
  STOCK_INACTIVE: 'The stock is inactive',
  REPOPULATION_EMPTY_RESULT: 'The provider returned no data',
  INTERNAL_ERROR: 'Internal error',
};

/**
 * Label of a job error code, or `undefined` for a code this release does not know yet (the wire value is a plain
 * string), which callers show as sent. Only own keys count, so a code such as "constructor" is unknown too.
 */
export function jobErrorLabel(code: string): string | undefined {
  return Object.hasOwn(JOB_ERROR_LABELS, code) ? JOB_ERROR_LABELS[code as MarketDataJobErrorCode] : undefined;
}

export const AUDIT_ACTION_LABELS: Record<AuditAction, string> = {
  USER_CREATED: 'User created',
  USER_UPDATED: 'User updated',
  USER_ROLE_CHANGED: 'Role changed',
  USER_DISABLED: 'User disabled',
  USER_ENABLED: 'User enabled',
  USER_LOCKED: 'User locked',
  USER_DELETED: 'User deleted',
  USER_PASSWORD_RESET: 'Password reset',
  STOCK_CREATED: 'Stock added',
  STOCK_DISABLED: 'Stock deactivated',
  STOCK_ENABLED: 'Stock activated',
  MARKET_DATA_REFRESH_STARTED: 'Latest-data update started',
  MARKET_DATA_BACKFILL_STARTED: 'Backfill started',
  MARKET_DATA_REPOPULATION_STARTED: 'Re-population started',
  MARKET_DATA_BULK_LOAD_STARTED: 'Bulk load started',
  MARKET_DATA_SYNC_TRIGGERED: 'Synchronization triggered',
  MARKET_DATA_JOB_RETRIED: 'Job retried',
  MARKET_DATA_JOB_CANCELLED: 'Job cancelled',
};
export const AUDIT_ACTIONS = Object.keys(AUDIT_ACTION_LABELS) as AuditAction[];

export const AUDIT_ENTITY_LABELS: Record<AuditEntityType, string> = {
  USER: 'User',
  STOCK: 'Stock',
  MARKET_DATA_JOB: 'Sync job',
  MARKET_DATA_PROVIDER: 'Provider',
};
export const AUDIT_ENTITY_TYPES = Object.keys(AUDIT_ENTITY_LABELS) as AuditEntityType[];

/** Short, recognisable job reference used in notifications and links ("Job #0199d1c4"). */
export const shortId = (id: string) => id.slice(0, 8);

/** Who performed an audited action or requested a job; administrators may have been deleted since. */
export function actorLabel(actor: { email: string | null } | null): string {
  if (!actor) return 'System';
  return actor.email ?? 'Deleted administrator';
}

/** A value from the URL counts only if it is one of the allowed options. */
export function parseOption<T extends string>(value: string | null, options: readonly T[]): T | undefined {
  return options.find((option) => option === value);
}
