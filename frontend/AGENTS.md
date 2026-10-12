# frontend/AGENTS.md — React SPA (StockLens)

Read the root [`AGENTS.md`](../AGENTS.md) first (golden rules, API contract, Definition of Done). This file adds
frontend specifics.

## Stack

React 19 · TypeScript **6.0** (pinned: `typescript-eslint` does not support the native TypeScript 7 compiler yet) ·
Vite 8 · MUI 9 · TanStack Query 5 · React Router 8 (`react-router` package, not `react-router-dom`) · Axios ·
React Hook Form + Zod 4 · Apache ECharts 6 · Vitest 5 + Testing Library + MSW 3 · ESLint 10 (flat config).

## Layout (feature-oriented)

```text
src/
├── app/            providers/ (query client, notifications, AppProviders), router/ (routes, lazyPages, guards,
│                   error pages), layout/ (AppShell, Sidebar, TopBar parts, DemoDataBanner)
├── api/            httpClient.ts (Axios + auth), apiError.ts, <area>Api.ts per backend area, queryKeys.ts
├── features/       auth · dashboard · watchlist · stocks · profile · compare · forecast · ai · admin
├── components/     charts/ (EChart, LazyEChart, echarts registration, useChartTheme) · tables/ · forms/ · common/
├── hooks/          useDebouncedValue, useSort
├── models/         TypeScript mirrors of backend DTOs
├── theme/          createAppTheme (light/dark), ColorModeProvider (preference in localStorage)
├── utils/          format.ts (all number/date formatting)
└── test/           setup.tsx, server.ts, http.ts (api()/apiError()), handlers.ts + adminHandlers.ts (MSW
                    defaults), fixtures.ts + adminFixtures.ts, signInAsAdmin.ts, renderApp.tsx
```

Put code in the feature that owns it. Only genuinely shared pieces go to `components/`, `hooks/` or `utils/`.

## Data flow

`models/` (types) → `api/<area>Api.ts` (one function per endpoint, returns typed data) → `api/queryKeys.ts` →
hooks in `features/<feature>/hooks.ts` (TanStack Query) → components.

- **Never call Axios from components.** Server state lives only in TanStack Query: no global client store for
  server data.
- Query keys come from `queryKeys`. After a mutation, invalidate everything it affects. For example, watchlist
  mutations invalidate `watchlists`, `dashboardAll`, `analyticsAll`, `pricesAll` and `forecastAll`, because
  watching a stock starts market-data collection.
- Poll only while data is being collected: `refetchInterval` returns a delay while `dataStatus === 'PENDING'`,
  otherwise `false` (`features/stocks/hooks.ts`).
- Errors reaching components are `ApiError` instances (`status`, `code`, `message`, `fieldErrors`,
  `correlationId`, `details`). Show the backend's message; map known `code`s to form fields where relevant, and use
  `details` for structured guidance (e.g. `INSUFFICIENT_FORECAST_HISTORY` → required vs available trading days via
  `historyShortfall` in `features/forecast/hooks.ts`).
- Never compute financial values on the client. Format what the backend returns with `utils/format.ts` (ratios are
  decimal fractions: `formatPercent(0.052)` → `+5.20%`).

## Authentication (do not scatter auth logic)

- `api/httpClient.ts` owns bearer headers, **refresh-and-retry once on 401** (single flight in the tab, Web Locks
  across tabs), error normalisation and session events. `/auth/*` calls are never retried and never carry a
  bearer token.
- The access token lives **in memory only** (`tokenStore`); never use localStorage or sessionStorage for tokens. The
  refresh token is an HttpOnly cookie the SPA cannot read; on load `AuthProvider` calls `/auth/refresh` (a 401 for
  anonymous visitors is expected).
- `features/auth/AuthProvider.tsx` owns the auth state; components use `useAuth()` from `authContext.ts`.
- Routing after authentication is owned by the guards (`app/router/guards.tsx`): `ProtectedRoute` sends anonymous
  users to `/login` with `state.from` and users with `passwordChangeRequired` to `/change-password`;
  `PublicOnlyRoute` sends signed-in users back to `from` (or the dashboard); `PasswordChangeRoute` moves users on once
  the password was changed. **Pages must not navigate after login, registration or a password change themselves.**
- Admin pages live under `/app/admin` behind `features/admin/AdminRoute.tsx` (in-shell 403 for non-administrators);
  the sidebar renders `ADMIN_NAV` only for `role === 'ADMIN'`. This is UX only: the backend authorizes every admin
  call. Admin API modules are in `api/admin/`, types in `models/admin.ts`, keys under `queryKeys.admin.*`.

## UI conventions

- MUI theme in `theme/theme.ts`: semantic palette `positive`, `negative`, `forecast`, `chart[]`. Use theme tokens
  (`'positive.main'`), never hard-coded colours in components.
- Signed changes always use `components/common/ChangeIndicator` (arrow + sign + colour + accessible label).
  Meaning must never depend on colour alone.
- Every data view has: a skeleton (`components/common/Skeletons`), an `EmptyState` with a next action, an
  `ErrorState` (backend message, reference id, Retry), and a pending ("Syncing…") state for market data.
- Synthetic data: `DemoDataBanner` shows whenever the provider reports `synthetic: true`; keep it.
- Pages end with `<Disclaimer />` wherever analytics, forecasts or AI output are shown. Forecast views use the
  forecasting disclaimer from the response (`FORECAST_DISCLAIMER` as fallback), phrase numbers as a median estimate
  and an estimated range, draw history solid, the median dashed and the range shaded
  (`features/forecast/forecastChartOption.ts`), and label backtests "historical", never "future".
- Responsive: tables become card lists below `sm` (`components/tables/StockMetricsTable`); the sidebar becomes a
  drawer below `md`.
- Accessibility: every input needs a real label. With MUI 9, put `aria-label` on the input through
  `slotProps.htmlInput`; on `TextField` itself it labels the wrapper, not the input. Use landmarks and headings,
  `aria-current` in navigation, and an `ariaLabel` describing every chart.
- Forms: React Hook Form + `zodResolver`; schemas mirror backend rules (password 12 characters to 72 UTF-8 bytes,
  watchlist name 1–60). Zod 4 syntax: `z.email({ error: '…' })`, `.min(n, { error: '…' })`.

## Charts

- Register ECharts modules once in `components/charts/echarts.ts` (tree-shaken); render with `LazyEChart` so the
  library loads on demand.
- Build options in **pure functions** (`*ChartOption.ts`) that take data and `useChartTheme()` colours (concrete
  colours, because canvas cannot use CSS variables), and unit-test them.
- Time-series line charts use a `time` x-axis. The upcoming candlestick view will need a `category` axis to avoid
  weekend gaps.

## Routing

- Routes are declared in `app/router/routes.tsx`; page components are lazily imported in
  `app/router/lazyPages.ts` (a separate file keeps the react-refresh lint rule happy).
- Add sidebar entries in `app/layout/navigation.ts` (`soon: true` marks features from later phases).

## Testing

- `renderApp(path)` (`test/renderApp.tsx`) renders the real providers, guards, layout and lazy pages in a memory
  router. Prefer it over rendering components in isolation.
- MSW 3: default handlers in `test/handlers.ts` model a signed-in **regular** user with two watchlists; admin
  endpoints have defaults in `test/adminHandlers.ts`. Call `signInAsAdmin()` (`test/signInAsAdmin.ts`) to start as an
  administrator. Override per test with `server.use(...)`. Build URLs with `api('/path')` (`test/http.ts`, matches
  `*/api/v1/path`). Register specific routes (`/stocks/search`, `/admin/stocks/provider-search`) **before**
  parameterised ones (`/stocks/:symbol`).
- Unhandled requests fail the test (`server.listen({ onUnhandledFrame: 'error' })`; MSW 3 renamed
  `onUnhandledRequest`).
- `components/charts/EChart` is mocked in `test/setup.tsx` (jsdom has no canvas) and renders
  `role="img"` with the chart's `ariaLabel`. Assert charts with `findByRole('img', { name })`.
- Lazy pages and charts load asynchronously: use `findBy*`/`waitFor`, never fixed sleeps. Debounced search
  (350 ms) works with real timers.
- Query by role and accessible name. If an element has no accessible name, that is a bug to fix in the component,
  not in the test.

## Library gotchas

- MUI 9: `slotProps` replaces `InputProps`/`inputProps`; `Grid` uses `size={{ xs: 12, md: 6 }}`; `Button` has
  `loading`; some icons were renamed (`DeleteOutlined`, `PersonOutlined`, `RemoveCircleOutlined`).
- The nginx Content-Security-Policy forbids `eval`, so `main.tsx` calls `z.config({ jitless: true })`.
- `VITE_API_BASE_URL` defaults to `/api/v1` (same origin through the Vite proxy or nginx); absolute URLs require the
  backend's `CORS_ALLOWED_ORIGINS`.

## Commands

```bash
npm ci
npm run dev                                   # http://localhost:5173 (proxy → http://localhost:8080)
npm run lint && npm run typecheck && npm test && npm run build
npx vitest run src/features/watchlist         # one folder or file
```
