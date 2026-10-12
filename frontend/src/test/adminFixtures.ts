import type {
  AdminDashboard, AdminStock, AdminStockMarketData, AdminUser, AdminUserDetails, AuditEvent, MarketDataJob,
  ProviderHealth, ProviderOverview, ProviderSearchResponse, ProviderSearchResult, UserStatistics,
} from '@/models/admin';
import type { Page } from '@/models/api';
import type { AuthResponse } from '@/models/auth';
import { session, stocks, USER_ID } from './fixtures';

/** In admin tests the signed-in administrator is Ada (the default user, promoted to ADMIN). */
export function adminSession(): AuthResponse {
  return session({ user: { id: USER_ID, email: 'ada@example.com', role: 'ADMIN', passwordChangeRequired: false } });
}

export function page<T>(content: T[], overrides: Partial<Page<T>['page']> = {}): Page<T> {
  return {
    content,
    page: { number: 0, size: 20, totalElements: content.length, totalPages: content.length === 0 ? 0 : 1, ...overrides },
  };
}

/* Users ------------------------------------------------------------------------------------------------------------ */

export const ALICE_ID = '0199d1c4-6d6f-7a51-9f43-2b9c7a0c1b11';
export const BOB_ID = '0199d1c4-7a00-7b11-8c22-3d4e5f6a7b8c';
export const CAROL_ID = '0199d1c4-8b11-7c22-9d33-4e5f6a7b8c9d';

export function adminUser(overrides: Partial<AdminUser> = {}): AdminUser {
  return {
    id: ALICE_ID,
    email: 'alice@example.com',
    displayName: 'Alice Smith',
    role: 'USER',
    status: 'ACTIVE',
    passwordChangeRequired: false,
    createdAt: '2026-10-01T08:00:00Z',
    updatedAt: '2026-10-02T08:00:00Z',
    lastLoginAt: null,
    ...overrides,
  };
}

export const adminUsers: AdminUser[] = [
  adminUser({
    id: USER_ID, email: 'ada@example.com', displayName: 'Ada Lovelace', role: 'ADMIN', createdAt: '2026-01-15T10:00:00Z',
    lastLoginAt: '2026-10-11T08:00:00Z',
  }),
  adminUser(),
  adminUser({ id: BOB_ID, email: 'bob@example.com', displayName: 'Bob Jones', status: 'DISABLED' }),
  adminUser({ id: CAROL_ID, email: 'carol@example.com', displayName: 'Carol White', status: 'LOCKED', passwordChangeRequired: true }),
];

export function adminUserDetails(overrides: Partial<AdminUserDetails> = {}): AdminUserDetails {
  const base = adminUsers.find((user) => user.id === overrides.id) ?? adminUser();
  return { ...base, timeZone: 'Europe/Berlin', watchlistCount: 2, ...overrides };
}

export const userStatistics: UserStatistics = {
  totalUsers: 128, administrators: 2, registeredLast30Days: 30, activeUsers: 121, disabledUsers: 6, lockedUsers: 1,
  registeredLast7Days: 12,
};

/* Dashboard -------------------------------------------------------------------------------------------------------- */

export function adminDashboard(overrides: Partial<AdminDashboard> = {}): AdminDashboard {
  return {
    generatedAt: '2026-10-11T22:50:00Z',
    users: { total: 128, active: 121, disabled: 6, locked: 1, administrators: 2, newLast7Days: 12 },
    stocks: { total: 69, active: 68, tracked: 40, withMarketData: 38, missingData: 2 },
    marketData: {
      provider: { id: 'twelve-data', name: 'Twelve Data', synthetic: false, status: 'HEALTHY', checkedAt: '2026-10-11T22:48:31Z' },
      lastSuccessfulSyncAt: '2026-10-11T22:45:02Z',
      lastFailedSyncAt: '2026-10-11T21:30:00Z',
      lastFailureMessage: 'Market-data provider timed out',
    },
    jobs: { queued: 0, running: 1, completedToday: 48, failedToday: 2 },
    ...overrides,
  };
}

/* Stocks ----------------------------------------------------------------------------------------------------------- */

type StockOverrides = Partial<Omit<AdminStock, 'marketData'>> & { marketData?: Partial<AdminStockMarketData> };

export function adminStock(symbol = 'AAPL', overrides: StockOverrides = {}): AdminStock {
  const stock = stocks[symbol];
  const { marketData, ...rest } = overrides;
  return {
    id: `stock-${symbol.toLowerCase()}`,
    symbol,
    companyName: stock?.companyName ?? `${symbol} Inc.`,
    exchange: stock?.exchange ?? 'NASDAQ',
    mic: stock?.mic ?? 'XNAS',
    currency: 'USD',
    country: 'US',
    sector: stock?.sector ?? null,
    industry: stock?.industry ?? null,
    assetType: 'COMMON_STOCK',
    active: true,
    source: 'CATALOGUE',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    watchers: 3,
    marketData: {
      dataStatus: 'READY',
      provider: 'twelve-data',
      firstBarDate: '2021-10-11',
      lastBarDate: '2026-10-09',
      barCount: 1258,
      lastSuccessAt: '2026-10-11T22:45:02Z',
      lastAttemptAt: '2026-10-11T22:45:01Z',
      lastError: null,
      activeJob: null,
      ...marketData,
    },
    ...rest,
  };
}

export const JOB_ID = 'f82d1a2b-6d6f-7a51-9f43-2b9c7a0c1b11';
export const FAILED_JOB_ID = 'c3d4e5f6-0000-7a51-9f43-2b9c7a0c1b11';
export const COMPLETED_JOB_ID = 'b2c3d4e5-0000-7a51-9f43-2b9c7a0c1b11';

export const adminStocks: AdminStock[] = [
  adminStock('AAPL'),
  adminStock('MSFT', { marketData: { activeJob: { id: JOB_ID, type: 'BACKFILL', status: 'RUNNING', progress: 40 } } }),
  adminStock('KO', {
    active: false, watchers: 0,
    marketData: {
      dataStatus: 'NOT_TRACKED', provider: null, firstBarDate: null, lastBarDate: null, barCount: 0, lastSuccessAt: null,
      lastAttemptAt: null,
    },
  }),
];

const providerInstruments: ProviderSearchResult[] = [
  { symbol: 'MSFT', name: 'Microsoft Corporation', exchange: 'NASDAQ', mic: 'XNGS', currency: 'USD', country: 'US', instrumentType: 'Common Stock', assetType: 'COMMON_STOCK', alreadyListed: true },
  { symbol: 'MSFU', name: 'Direxion Daily MSFT Bull 2X Shares', exchange: 'NASDAQ', mic: 'XNMS', currency: 'USD', country: 'US', instrumentType: 'Leveraged ETF', assetType: null, alreadyListed: false },
  { symbol: 'SNOW', name: 'Snowflake Inc.', exchange: 'NYSE', mic: 'XNYS', currency: 'USD', country: 'US', instrumentType: 'Common Stock', assetType: 'COMMON_STOCK', alreadyListed: false },
  { symbol: 'SAP', name: 'SAP SE', exchange: 'XETRA', mic: 'XETR', currency: 'EUR', country: 'DE', instrumentType: 'Common Stock', assetType: 'COMMON_STOCK', alreadyListed: false },
];

export function providerSearch(query: string, overrides: Partial<ProviderSearchResponse> = {}): ProviderSearchResponse {
  const text = query.toUpperCase();
  return {
    provider: { id: 'twelve-data', name: 'Twelve Data' },
    supported: true,
    results: providerInstruments.filter((result) => result.symbol.includes(text) || result.name.toUpperCase().includes(text)),
    ...overrides,
  };
}

/* Jobs ------------------------------------------------------------------------------------------------------------- */

export function marketDataJob(overrides: Partial<MarketDataJob> = {}): MarketDataJob {
  return {
    id: JOB_ID,
    type: 'BACKFILL',
    status: 'RUNNING',
    trigger: 'ADMIN',
    stockId: 'stock-msft',
    symbol: 'MSFT',
    companyName: 'Microsoft Corporation',
    provider: 'twelve-data',
    interval: 'ONE_DAY',
    existingDataStrategy: 'SKIP',
    fromDate: '2015-01-01',
    toDate: '2026-10-09',
    requestedBy: { id: USER_ID, email: 'ada@example.com' },
    requestedAt: '2026-10-11T23:02:00Z',
    startedAt: '2026-10-11T23:02:01Z',
    completedAt: null,
    durationMs: null,
    progress: 40,
    statusMessage: 'Fetching 2015-01-01 → 2026-10-09 from Twelve Data',
    attempts: 1,
    maxAttempts: 5,
    nextAttemptAt: null,
    recordsFetched: 0,
    recordsInserted: 0,
    recordsUpdated: 0,
    recordsSkipped: 0,
    recordsFailed: 0,
    recordsDeleted: 0,
    errorCode: null,
    errorMessage: null,
    retryable: false,
    retryOfJobId: null,
    batchId: null,
    ...overrides,
  };
}

export const marketDataJobs: MarketDataJob[] = [
  marketDataJob(),
  marketDataJob({
    id: COMPLETED_JOB_ID, type: 'LATEST_UPDATE', status: 'COMPLETED', trigger: 'SCHEDULE', symbol: 'AAPL',
    stockId: 'stock-aapl', companyName: 'Apple Inc.', requestedBy: null, existingDataStrategy: null, fromDate: '2026-10-05',
    toDate: '2026-10-09', completedAt: '2026-10-11T22:45:06Z', durationMs: 4_200, progress: 100,
    statusMessage: 'Stored 1 new bar', recordsFetched: 5, recordsInserted: 1, recordsSkipped: 4,
  }),
  marketDataJob({
    id: FAILED_JOB_ID, type: 'INITIAL_LOAD', status: 'FAILED', trigger: 'WATCHLIST', symbol: 'NVDA', stockId: 'stock-nvda',
    companyName: 'NVIDIA Corporation', requestedBy: null, existingDataStrategy: null, progress: 10, attempts: 5,
    completedAt: '2026-10-11T21:30:00Z', durationMs: 95_000, statusMessage: 'Gave up after 5 attempts',
    errorCode: 'PROVIDER_TIMEOUT', errorMessage: 'Market-data provider timed out', retryable: true,
  }),
];

/* Providers -------------------------------------------------------------------------------------------------------- */

export function providerHealth(overrides: Partial<ProviderHealth> = {}): ProviderHealth {
  return {
    provider: 'twelve-data',
    status: 'HEALTHY',
    checkedAt: '2026-10-11T22:48:31Z',
    latencyMs: 231,
    message: 'Provider reachable; 3 of 8 requests used this minute',
    details: { planCategory: 'basic', minuteUsage: 3, minuteLimit: 8, dailyUsage: 120, dailyLimit: 800 },
    ...overrides,
  };
}

export function providerOverview(overrides: Partial<ProviderOverview['active']> = {}): ProviderOverview {
  return {
    active: {
      id: 'twelve-data',
      name: 'Twelve Data',
      synthetic: false,
      description: 'Licensed end-of-day prices from Twelve Data',
      apiKeyRequired: true,
      apiKeyConfigured: true,
      capabilities: {
        supportedIntervals: ['ONE_DAY'],
        symbolSearch: true,
        metadataLookup: true,
        latestQuote: true,
        maxBarsPerRequest: 5000,
        adjustment: 'OHLC adjusted for splits; adjusted close adjusted for splits and dividends',
      },
      health: providerHealth(),
      usage: {
        requestsToday: 120,
        failuresToday: 2,
        rateLimitedToday: 1,
        lastSuccessAt: '2026-10-11T22:48:31Z',
        lastFailureAt: '2026-10-11T21:30:00Z',
        lastFailureMessage: 'Market-data provider timed out',
        rateLimit: { requestsPerMinute: 8, availablePermits: 5, throttledUntil: null },
      },
      ...overrides,
    },
    available: [
      { id: 'synthetic', name: 'Synthetic demo data', active: false },
      { id: 'twelve-data', name: 'Twelve Data', active: true },
    ],
    configurationHint: 'Set MARKET_DATA_PROVIDER (and TWELVE_DATA_API_KEY) and restart the backend to switch providers.',
  };
}

/* Audit log -------------------------------------------------------------------------------------------------------- */

export function auditEvent(overrides: Partial<AuditEvent> = {}): AuditEvent {
  return {
    id: 'a0d1e2f3-0000-7a51-9f43-2b9c7a0c1b11',
    occurredAt: '2026-10-11T22:50:00Z',
    actor: { id: USER_ID, email: 'ada@example.com' },
    action: 'USER_DISABLED',
    entityType: 'USER',
    entityId: ALICE_ID,
    summary: 'Disabled alice@example.com',
    metadata: { previousStatus: 'ACTIVE', newStatus: 'DISABLED' },
    correlationId: 'c0ffee00-1234-5678-9abc-def012345678',
    ...overrides,
  };
}

export const auditEvents: AuditEvent[] = [
  auditEvent(),
  auditEvent({
    id: 'b1e2f3a4-0000-7a51-9f43-2b9c7a0c1b11', occurredAt: '2026-10-11T21:00:00Z', action: 'STOCK_CREATED',
    entityType: 'STOCK', entityId: 'stock-volv-b', summary: 'Added VOLV-B (Volvo B)', metadata: { source: 'MANUAL', loadHistory: true },
  }),
  auditEvent({
    id: 'c2f3a4b5-0000-7a51-9f43-2b9c7a0c1b11', occurredAt: '2026-10-11T20:00:00Z', action: 'MARKET_DATA_BACKFILL_STARTED',
    entityType: 'MARKET_DATA_JOB', entityId: JOB_ID, summary: 'Backfill of MSFT queued',
    metadata: { symbol: 'MSFT', range: { from: '2015-01-01', to: '2026-10-09' } },
  }),
];
