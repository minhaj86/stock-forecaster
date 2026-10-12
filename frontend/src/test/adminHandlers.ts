import { http, HttpResponse } from 'msw';
import type {
  AdminStock, BackfillRequest, BulkLoadRequest, CreateStockRequest, CreateUserRequest, MarketDataJobType,
  RepopulateRequest, ResetPasswordRequest, UpdateUserRequest,
} from '@/models/admin';
import * as admin from './adminFixtures';
import { api, apiError } from './http';

/** Id of the job created by a retry in the default handlers. */
export const RETRIED_JOB_ID = 'd4e5f6a7-0000-7a51-9f43-2b9c7a0c1b11';

const NOW = '2026-10-11T23:10:00Z';

/** Recognisable id prefixes per job type ("Job #3c4d5e6f" is a queued backfill). */
const JOB_ID_PREFIXES: Record<MarketDataJobType, string> = {
  INITIAL_LOAD: '1a2b3c4d', LATEST_UPDATE: '2b3c4d5e', BACKFILL: '3c4d5e6f', REPOPULATE: '4d5e6f70', AUTOMATIC_SYNC: '5e6f7081',
};

const userNotFound = (id: unknown) => apiError(404, 'USER_NOT_FOUND', `No user with id '${String(id)}' exists`);
const stockNotFound = (symbol: unknown) =>
  apiError(404, 'STOCK_NOT_FOUND', `No stock with symbol '${String(symbol)}' is available`);
const jobNotFound = (id: unknown) => apiError(404, 'MARKET_DATA_JOB_NOT_FOUND', `No market-data job with id '${String(id)}' exists`);

const findUser = (id: unknown) => admin.adminUsers.find((user) => user.id === String(id));
const findStock = (symbol: unknown) => admin.adminStocks.find((stock) => stock.symbol === String(symbol).toUpperCase());
const findJob = (id: unknown) => admin.marketDataJobs.find((job) => job.id === String(id));

/** A freshly queued job, as returned for every accepted market-data request. */
function queuedJob(type: MarketDataJobType, stock: AdminStock, range: { fromDate?: string; toDate?: string } = {}) {
  return admin.marketDataJob({
    id: `${JOB_ID_PREFIXES[type]}-${stock.symbol.toLowerCase().padEnd(4, '0').slice(0, 4)}-7a51-9f43-2b9c7a0c1b11`,
    type, status: 'QUEUED', symbol: stock.symbol, companyName: stock.companyName, stockId: stock.id,
    existingDataStrategy: type === 'BACKFILL' ? 'SKIP' : null, fromDate: range.fromDate ?? null, toDate: range.toDate ?? null,
    requestedAt: NOW, startedAt: null, progress: 0, statusMessage: 'Waiting for the job runner',
  });
}

/** Default responses of the admin console API; tests override what they exercise. */
export const adminHandlers = [
  http.get(api('/admin/dashboard'), () => HttpResponse.json(admin.adminDashboard())),
  http.post(api('/admin/market-data/synchronize'), () => HttpResponse.json({ stocksConsidered: 40, jobsQueued: 12 }, { status: 202 })),

  // Users: the specific /statistics path before /:id.
  http.get(api('/admin/users/statistics'), () => HttpResponse.json(admin.userStatistics)),
  http.get(api('/admin/users'), () => HttpResponse.json(admin.page(admin.adminUsers))),
  http.post(api('/admin/users'), async ({ request }) => {
    const body = (await request.json()) as CreateUserRequest;
    return HttpResponse.json(admin.adminUserDetails({
      id: '0199d1c4-9c22-7d33-8e44-5f6a7b8c9d0e', displayName: body.displayName, email: body.email, role: body.role,
      status: body.status, passwordChangeRequired: body.requirePasswordChange, createdAt: NOW, updatedAt: NOW,
      lastLoginAt: null, watchlistCount: 0,
    }), { status: 201 });
  }),
  http.get(api('/admin/users/:id'), ({ params }) => {
    const user = findUser(params.id);
    return user ? HttpResponse.json(admin.adminUserDetails(user)) : userNotFound(params.id);
  }),
  http.patch(api('/admin/users/:id'), async ({ params, request }) => {
    const user = findUser(params.id);
    if (!user) return userNotFound(params.id);
    const changes = (await request.json()) as UpdateUserRequest;
    return HttpResponse.json(admin.adminUserDetails({ ...user, ...changes, updatedAt: NOW }));
  }),
  http.post(api('/admin/users/:id/disable'), ({ params }) => {
    const user = findUser(params.id);
    return user ? HttpResponse.json(admin.adminUserDetails({ ...user, status: 'DISABLED' })) : userNotFound(params.id);
  }),
  http.post(api('/admin/users/:id/enable'), ({ params }) => {
    const user = findUser(params.id);
    return user ? HttpResponse.json(admin.adminUserDetails({ ...user, status: 'ACTIVE' })) : userNotFound(params.id);
  }),
  http.post(api('/admin/users/:id/password-reset'), async ({ params, request }) => {
    const user = findUser(params.id);
    if (!user) return userNotFound(params.id);
    const body = (await request.json()) as ResetPasswordRequest;
    return HttpResponse.json(admin.adminUserDetails({ ...user, passwordChangeRequired: body.requirePasswordChange }));
  }),
  http.delete(api('/admin/users/:id'), ({ params }) =>
    (findUser(params.id) ? new HttpResponse(null, { status: 204 }) : userNotFound(params.id))),

  // Stocks: the specific /provider-search path before /:symbol.
  http.get(api('/admin/stocks/provider-search'), ({ request }) =>
    HttpResponse.json(admin.providerSearch(new URL(request.url).searchParams.get('query') ?? ''))),
  http.get(api('/admin/stocks'), ({ request }) => {
    const params = new URL(request.url).searchParams;
    const query = (params.get('query') ?? '').toUpperCase();
    const status = params.get('status');
    const matches = admin.adminStocks.filter((stock) =>
      (stock.symbol.includes(query) || stock.companyName.toUpperCase().includes(query))
      && (!status || (status === 'ACTIVE') === stock.active));
    return HttpResponse.json(admin.page(matches));
  }),
  http.post(api('/admin/stocks'), async ({ request }) => {
    const body = (await request.json()) as CreateStockRequest;
    const symbol = body.symbol.toUpperCase();
    if (findStock(symbol)) return apiError(409, 'STOCK_ALREADY_EXISTS', `A stock with symbol '${symbol}' already exists`);
    const details = 'manual' in body ? body.manual : null;
    const stock = admin.adminStock(symbol, {
      companyName: details?.companyName ?? `${symbol} Inc.`, exchange: details?.exchange ?? 'NYSE',
      mic: details?.mic ?? ('mic' in body ? body.mic : 'XNYS'), source: details ? 'MANUAL' : 'twelve-data', watchers: 0,
      marketData: {
        dataStatus: body.loadHistory ? 'PENDING' : 'NOT_TRACKED', provider: null, firstBarDate: null, lastBarDate: null,
        barCount: 0, lastSuccessAt: null, lastAttemptAt: null,
      },
    });
    return HttpResponse.json({ stock, job: body.loadHistory ? queuedJob('INITIAL_LOAD', stock) : null }, { status: 201 });
  }),
  http.get(api('/admin/stocks/:symbol'), ({ params }) => {
    const stock = findStock(params.symbol);
    return stock ? HttpResponse.json(stock) : stockNotFound(params.symbol);
  }),
  http.post(api('/admin/stocks/:symbol/activate'), ({ params }) => {
    const stock = findStock(params.symbol);
    return stock ? HttpResponse.json({ ...stock, active: true }) : stockNotFound(params.symbol);
  }),
  http.post(api('/admin/stocks/:symbol/deactivate'), ({ params }) => {
    const stock = findStock(params.symbol);
    return stock ? HttpResponse.json({ ...stock, active: false }) : stockNotFound(params.symbol);
  }),
  http.post(api('/admin/stocks/:symbol/market-data/update'), ({ params }) => {
    const stock = findStock(params.symbol);
    return stock ? HttpResponse.json({ job: queuedJob('LATEST_UPDATE', stock), created: true }, { status: 202 })
      : stockNotFound(params.symbol);
  }),
  http.post(api('/admin/stocks/:symbol/market-data/backfill'), async ({ params, request }) => {
    const stock = findStock(params.symbol);
    if (!stock) return stockNotFound(params.symbol);
    const body = (await request.json()) as BackfillRequest;
    return HttpResponse.json({ job: queuedJob('BACKFILL', stock, body), created: true }, { status: 202 });
  }),
  http.post(api('/admin/stocks/:symbol/market-data/repopulate'), async ({ params, request }) => {
    const stock = findStock(params.symbol);
    if (!stock) return stockNotFound(params.symbol);
    const body = (await request.json()) as RepopulateRequest;
    return HttpResponse.json({ job: queuedJob('REPOPULATE', stock, body), created: true }, { status: 202 });
  }),

  // Market-data jobs and providers: /providers/health before /providers is not ambiguous, /jobs/:jobId is.
  http.post(api('/admin/market-data/bulk-load'), async ({ request }) => {
    const body = (await request.json()) as BulkLoadRequest;
    const unknown = body.symbols.find((symbol) => !findStock(symbol));
    if (unknown) return stockNotFound(unknown);
    const jobs = body.symbols.map((symbol) => ({
      ...queuedJob('BACKFILL', findStock(symbol) as AdminStock, body), batchId: 'e5f6a7b8-0000-7a51-9f43-2b9c7a0c1b11',
    }));
    return HttpResponse.json({ batchId: 'e5f6a7b8-0000-7a51-9f43-2b9c7a0c1b11', jobs }, { status: 202 });
  }),
  http.get(api('/admin/market-data/jobs'), () => HttpResponse.json(admin.page(admin.marketDataJobs))),
  http.get(api('/admin/market-data/jobs/:jobId'), ({ params }) => {
    const job = findJob(params.jobId);
    return job ? HttpResponse.json(job) : jobNotFound(params.jobId);
  }),
  http.post(api('/admin/market-data/jobs/:jobId/retry'), ({ params }) => {
    const job = findJob(params.jobId);
    if (!job) return jobNotFound(params.jobId);
    if (!job.retryable) return apiError(409, 'MARKET_DATA_JOB_NOT_RETRYABLE', 'Only failed jobs with a temporary error can be retried');
    return HttpResponse.json({
      job: {
        ...job, id: RETRIED_JOB_ID, status: 'QUEUED', trigger: 'ADMIN', retryOfJobId: job.id, attempts: 0, progress: 0,
        errorCode: null, errorMessage: null, retryable: false, requestedAt: NOW, startedAt: null, completedAt: null, durationMs: null,
      },
      created: true,
    }, { status: 202 });
  }),
  http.post(api('/admin/market-data/jobs/:jobId/cancel'), ({ params }) => {
    const job = findJob(params.jobId);
    if (!job) return jobNotFound(params.jobId);
    return job.status === 'QUEUED' ? HttpResponse.json({ ...job, status: 'CANCELLED', completedAt: NOW })
      : apiError(409, 'MARKET_DATA_JOB_NOT_CANCELLABLE', 'Only queued jobs can be cancelled');
  }),
  http.get(api('/admin/market-data/providers/health'), () => HttpResponse.json(admin.providerHealth())),
  http.get(api('/admin/market-data/providers'), () => HttpResponse.json(admin.providerOverview())),

  http.get(api('/admin/audit-log'), () => HttpResponse.json(admin.page(admin.auditEvents))),
];
