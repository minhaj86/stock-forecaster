import type { Role } from './auth';
import type { AssetType } from './stock';

/**
 * TypeScript mirrors of the admin console API (`/api/v1/admin/**`). Instants are ISO-8601 UTC strings, calendar dates
 * `yyyy-MM-dd`; `null` marks unavailable values.
 */

/** Values of free-form maps such as audit metadata and provider health details. */
export type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };

/* ------------------------------------------------------------------------------------------------------------------ */
/* Shared enums                                                                                                        */
/* ------------------------------------------------------------------------------------------------------------------ */

export type AccountStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED';

/** Last known result of a provider health check; `UNKNOWN` = never checked. */
export type ProviderHealthStatus = 'HEALTHY' | 'DEGRADED' | 'UNAVAILABLE' | 'MISCONFIGURED' | 'UNKNOWN';

/** Availability of stored market data for a stock. */
export type MarketDataStatus = 'READY' | 'PENDING' | 'FAILED' | 'NOT_TRACKED';

export type MarketDataJobType = 'INITIAL_LOAD' | 'LATEST_UPDATE' | 'BACKFILL' | 'REPOPULATE' | 'AUTOMATIC_SYNC';
export type MarketDataJobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'PARTIALLY_COMPLETED' | 'FAILED' | 'CANCELLED';
export type MarketDataJobTrigger = 'ADMIN' | 'WATCHLIST' | 'SCHEDULE';
/** Only daily bars today; rendered as "Daily". */
export type MarketDataInterval = 'ONE_DAY';
export type ExistingDataStrategy = 'SKIP' | 'UPDATE';
/**
 * Known causes of failed or retried jobs. On the wire `errorCode` is a plain string, so newer backends may send codes
 * that are not listed here: look labels up with `jobErrorLabel` and show unknown codes as sent.
 */
export type MarketDataJobErrorCode =
  | 'PROVIDER_TIMEOUT'
  | 'PROVIDER_RATE_LIMITED'
  | 'PROVIDER_UNAVAILABLE'
  | 'PROVIDER_AUTHENTICATION_FAILED'
  | 'PROVIDER_PLAN_RESTRICTED'
  | 'PROVIDER_NOT_CONFIGURED'
  /** The provider rejected the request parameters; not retryable. */
  | 'PROVIDER_REJECTED_REQUEST'
  | 'SYMBOL_NOT_FOUND'
  | 'INVALID_PROVIDER_RESPONSE'
  | 'INVALID_PROVIDER_DATA'
  | 'UNSUPPORTED_INTERVAL'
  | 'STOCK_INACTIVE'
  | 'REPOPULATION_EMPTY_RESULT'
  | 'INTERNAL_ERROR';

/* ------------------------------------------------------------------------------------------------------------------ */
/* Dashboard: GET /admin/dashboard, POST /admin/market-data/synchronize                                                */
/* ------------------------------------------------------------------------------------------------------------------ */

export interface AdminDashboardUsers {
  total: number;
  active: number;
  disabled: number;
  locked: number;
  administrators: number;
  newLast7Days: number;
}

export interface AdminDashboardStocks {
  total: number;
  active: number;
  /** Stocks whose market data has been requested (watched or populated by an admin). */
  tracked: number;
  /** Tracked stocks with at least one stored bar. */
  withMarketData: number;
  /** tracked − withMarketData. */
  missingData: number;
}

export interface AdminDashboardProvider {
  id: string;
  name: string;
  synthetic: boolean;
  status: ProviderHealthStatus;
  checkedAt: string | null;
}

export interface AdminDashboardMarketData {
  provider: AdminDashboardProvider;
  lastSuccessfulSyncAt: string | null;
  lastFailedSyncAt: string | null;
  lastFailureMessage: string | null;
}

/** "Today" = since 00:00 UTC. */
export interface AdminDashboardJobs {
  queued: number;
  running: number;
  completedToday: number;
  failedToday: number;
}

export interface AdminDashboard {
  generatedAt: string;
  users: AdminDashboardUsers;
  stocks: AdminDashboardStocks;
  marketData: AdminDashboardMarketData;
  jobs: AdminDashboardJobs;
}

export interface SynchronizationResult {
  stocksConsidered: number;
  jobsQueued: number;
}

/* ------------------------------------------------------------------------------------------------------------------ */
/* Users: /admin/users                                                                                                 */
/* ------------------------------------------------------------------------------------------------------------------ */

export interface AdminUser {
  id: string;
  email: string;
  /** `null` only for inconsistent legacy data. */
  displayName: string | null;
  role: Role;
  status: AccountStatus;
  passwordChangeRequired: boolean;
  createdAt: string;
  updatedAt: string;
  lastLoginAt: string | null;
}

export interface AdminUserDetails extends AdminUser {
  timeZone: string;
  watchlistCount: number;
}

export interface UserStatistics {
  totalUsers: number;
  administrators: number;
  registeredLast30Days: number;
  activeUsers: number;
  disabledUsers: number;
  lockedUsers: number;
  registeredLast7Days: number;
}

/** Query of `GET /admin/users`; `page` is 0-based, `createdFrom`/`createdTo` are inclusive `yyyy-MM-dd`. */
export interface AdminUserFilters {
  query?: string;
  role?: Role;
  status?: AccountStatus;
  createdFrom?: string;
  createdTo?: string;
  page: number;
  size: number;
}

export interface CreateUserRequest {
  displayName: string;
  email: string;
  role: Role;
  status: AccountStatus;
  temporaryPassword: string;
  requirePasswordChange: boolean;
}

/** Any subset of the editable fields. */
export interface UpdateUserRequest {
  displayName?: string;
  email?: string;
  role?: Role;
  status?: AccountStatus;
}

export interface ResetPasswordRequest {
  temporaryPassword: string;
  requirePasswordChange: boolean;
}

/* ------------------------------------------------------------------------------------------------------------------ */
/* Stocks: /admin/stocks                                                                                               */
/* ------------------------------------------------------------------------------------------------------------------ */

/** The queued or running job of a stock. */
export interface ActiveMarketDataJob {
  id: string;
  type: MarketDataJobType;
  status: MarketDataJobStatus;
  /** 0–100. */
  progress: number;
}

export interface AdminStockMarketData {
  dataStatus: MarketDataStatus;
  provider: string | null;
  firstBarDate: string | null;
  lastBarDate: string | null;
  /** 0 when no data is stored. */
  barCount: number;
  lastSuccessAt: string | null;
  lastAttemptAt: string | null;
  lastError: string | null;
  /** `null` when no job is queued or running for the stock. */
  activeJob: ActiveMarketDataJob | null;
}

export interface AdminStock {
  id: string;
  symbol: string;
  companyName: string;
  exchange: string;
  mic: string;
  currency: string;
  country: string | null;
  sector: string | null;
  industry: string | null;
  assetType: AssetType;
  active: boolean;
  /** `CATALOGUE` (seeded), `MANUAL` (typed by an admin) or a provider id such as `twelve-data`. */
  source: string;
  createdAt: string;
  updatedAt: string;
  watchers: number;
  marketData: AdminStockMarketData;
}

export type StockActivityFilter = 'ACTIVE' | 'INACTIVE';

export interface AdminStockFilters {
  query?: string;
  status?: StockActivityFilter;
  page: number;
  size: number;
}

export interface ProviderSearchResult {
  symbol: string;
  name: string;
  exchange: string;
  mic: string;
  currency: string;
  /** ISO-3166 alpha-2 code. */
  country: string | null;
  instrumentType: string;
  /** `null` when the instrument type is not supported: shown, but it cannot be added. */
  assetType: AssetType | null;
  alreadyListed: boolean;
}

export interface ProviderSearchResponse {
  provider: { id: string; name: string };
  /** `false` when the provider cannot search (e.g. synthetic demo data): offer manual entry. */
  supported: boolean;
  results: ProviderSearchResult[];
}

export interface ManualStockDetails {
  companyName: string;
  exchange: string;
  mic: string;
  currency: string;
  country: string;
  sector: string | null;
  industry: string | null;
  assetType: AssetType;
}

/** Provider mode: the backend fetches the metadata from the market-data provider. */
export interface ProviderStockRequest {
  symbol: string;
  mic: string;
  loadHistory: boolean;
}

/** Manual mode: the administrator supplies the metadata. */
export interface ManualStockRequest {
  symbol: string;
  loadHistory: boolean;
  manual: ManualStockDetails;
}

export type CreateStockRequest = ProviderStockRequest | ManualStockRequest;

export interface CreateStockResponse {
  stock: AdminStock;
  /** The `INITIAL_LOAD` job queued when `loadHistory` was requested. */
  job: MarketDataJob | null;
}

/* ------------------------------------------------------------------------------------------------------------------ */
/* Market-data jobs: /admin/stocks/{symbol}/market-data/*, /admin/market-data/*                                       */
/* ------------------------------------------------------------------------------------------------------------------ */

export interface JobRequester {
  id: string;
  /** `null` if the administrator has been deleted. */
  email: string | null;
}

export interface MarketDataJob {
  id: string;
  type: MarketDataJobType;
  status: MarketDataJobStatus;
  trigger: MarketDataJobTrigger;
  stockId: string;
  symbol: string;
  companyName: string;
  provider: string;
  interval: MarketDataInterval;
  /** `null` for job types where it does not apply. */
  existingDataStrategy: ExistingDataStrategy | null;
  fromDate: string | null;
  toDate: string | null;
  /** `null` for system jobs (`WATCHLIST`, `SCHEDULE`). */
  requestedBy: JobRequester | null;
  requestedAt: string;
  startedAt: string | null;
  completedAt: string | null;
  /** Set once the job has finished. */
  durationMs: number | null;
  /** 0–100. */
  progress: number;
  statusMessage: string | null;
  attempts: number;
  maxAttempts: number;
  nextAttemptAt: string | null;
  recordsFetched: number;
  recordsInserted: number;
  recordsUpdated: number;
  recordsSkipped: number;
  recordsFailed: number;
  recordsDeleted: number;
  errorCode: MarketDataJobErrorCode | null;
  /** Sanitised for display. */
  errorMessage: string | null;
  /** The job failed with a transient error and may be retried manually. */
  retryable: boolean;
  retryOfJobId: string | null;
  batchId: string | null;
}

/** `created: false` = an identical job was already queued or running and is returned instead of a duplicate. */
export interface JobSubmission {
  job: MarketDataJob;
  created: boolean;
}

export interface MarketDataJobFilters {
  status?: MarketDataJobStatus;
  type?: MarketDataJobType;
  trigger?: MarketDataJobTrigger;
  symbol?: string;
  page: number;
  size: number;
}

export interface BackfillRequest {
  fromDate: string;
  toDate: string;
  interval: MarketDataInterval;
  existingDataStrategy: ExistingDataStrategy;
}

export interface RepopulateRequest {
  fromDate: string;
  toDate: string;
  interval: MarketDataInterval;
}

export interface BulkLoadRequest {
  /** 1–50 symbols. */
  symbols: string[];
  fromDate: string;
  toDate: string;
  interval: MarketDataInterval;
  existingDataStrategy: ExistingDataStrategy;
}

export interface BulkLoadResponse {
  batchId: string;
  jobs: MarketDataJob[];
}

/* ------------------------------------------------------------------------------------------------------------------ */
/* Providers: /admin/market-data/providers                                                                             */
/* ------------------------------------------------------------------------------------------------------------------ */

export interface ProviderCapabilities {
  supportedIntervals: MarketDataInterval[];
  symbolSearch: boolean;
  metadataLookup: boolean;
  latestQuote: boolean;
  maxBarsPerRequest: number;
  adjustment: string;
}

export interface ProviderHealth {
  provider: string;
  status: ProviderHealthStatus;
  checkedAt: string | null;
  latencyMs: number | null;
  message: string;
  /** Free-form string → value map; may be empty. */
  details: Record<string, JsonValue>;
}

export interface ProviderRateLimit {
  requestsPerMinute: number;
  availablePermits: number;
  throttledUntil: string | null;
}

export interface ProviderUsage {
  requestsToday: number;
  failuresToday: number;
  rateLimitedToday: number;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  lastFailureMessage: string | null;
  rateLimit: ProviderRateLimit;
}

/** The API key itself is never returned, only whether one is needed and configured. */
export interface ActiveProvider {
  id: string;
  name: string;
  synthetic: boolean;
  description: string;
  /** `false` for providers without credentials (the synthetic demo provider). */
  apiKeyRequired: boolean;
  apiKeyConfigured: boolean;
  capabilities: ProviderCapabilities;
  health: ProviderHealth;
  usage: ProviderUsage;
}

export interface AvailableProvider {
  id: string;
  name: string;
  active: boolean;
}

export interface ProviderOverview {
  active: ActiveProvider;
  available: AvailableProvider[];
  configurationHint: string;
}

/* ------------------------------------------------------------------------------------------------------------------ */
/* Audit log: GET /admin/audit-log                                                                                     */
/* ------------------------------------------------------------------------------------------------------------------ */

export type AuditAction =
  | 'USER_CREATED'
  | 'USER_UPDATED'
  | 'USER_ROLE_CHANGED'
  | 'USER_DISABLED'
  | 'USER_ENABLED'
  | 'USER_LOCKED'
  | 'USER_DELETED'
  | 'USER_PASSWORD_RESET'
  | 'STOCK_CREATED'
  | 'STOCK_DISABLED'
  | 'STOCK_ENABLED'
  | 'MARKET_DATA_REFRESH_STARTED'
  | 'MARKET_DATA_BACKFILL_STARTED'
  | 'MARKET_DATA_REPOPULATION_STARTED'
  | 'MARKET_DATA_BULK_LOAD_STARTED'
  | 'MARKET_DATA_SYNC_TRIGGERED'
  | 'MARKET_DATA_JOB_RETRIED'
  | 'MARKET_DATA_JOB_CANCELLED';

export type AuditEntityType = 'USER' | 'STOCK' | 'MARKET_DATA_JOB' | 'MARKET_DATA_PROVIDER';

export interface AuditActor {
  id: string;
  email: string | null;
}

export interface AuditEvent {
  id: string;
  occurredAt: string;
  actor: AuditActor | null;
  action: AuditAction;
  entityType: AuditEntityType;
  entityId: string | null;
  summary: string;
  /** Strings, numbers, booleans or nested objects; never secrets. */
  metadata: Record<string, JsonValue>;
  correlationId: string | null;
}

/** `actor` = contains-match on the actor e-mail; `from`/`to` inclusive `yyyy-MM-dd` (UTC). */
export interface AuditLogFilters {
  action?: AuditAction;
  entityType?: AuditEntityType;
  entityId?: string;
  actor?: string;
  from?: string;
  to?: string;
  page: number;
  size: number;
}
