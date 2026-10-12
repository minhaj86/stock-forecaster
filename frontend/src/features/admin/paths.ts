/** Routes of the admin console (all inside the authenticated app shell). */
export const adminPaths = {
  overview: '/app/admin',
  users: '/app/admin/users',
  user: (id: string) => `/app/admin/users/${encodeURIComponent(id)}`,
  stocks: '/app/admin/stocks',
  stock: (symbol: string) => `/app/admin/stocks/${encodeURIComponent(symbol)}`,
  marketData: '/app/admin/market-data',
  jobs: '/app/admin/sync-jobs',
  job: (jobId: string) => `/app/admin/sync-jobs/${encodeURIComponent(jobId)}`,
  providers: '/app/admin/providers',
  auditLog: '/app/admin/audit-log',
};
