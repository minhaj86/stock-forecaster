---
name: frontend-feature
description: Step-by-step guide for adding or changing screens, components and data hooks in the StockLens React frontend (types, API client function, query keys, TanStack Query hooks, MUI page with loading/empty/error states, routing, accessibility, Vitest + MSW tests). Use when implementing or modifying code under frontend/.
---

# Frontend feature (React 19 · MUI 9 · TanStack Query)

Read `frontend/AGENTS.md` first. Good templates: `features/watchlist/` (CRUD with dialogs and mutations),
`features/dashboard/` (composite read view and chart), `features/stocks/StockSearch.tsx` (debounced search).

## 1. Contract and types

- Confirm the backend endpoint exists (controller or `http://localhost:8080/v3/api-docs`). If it does not, build it
  first (`backend-feature` skill). Never invent a contract.
- Add or extend types in `src/models/<area>.ts` so they mirror the backend JSON exactly (nullable fields as
  `T | null`, ratios as `number`, dates as `string`).

## 2. API function and query keys

```ts
// src/api/thingsApi.ts
export const thingsApi = {
  list: async (): Promise<Thing[]> => (await httpClient.get<Thing[]>('/things')).data,
  create: async (name: string): Promise<Thing> => (await httpClient.post<Thing>('/things', { name })).data,
};
// src/api/queryKeys.ts
things: ['things'] as const,
thing: (id: string) => ['things', id] as const,
```

`httpClient` already adds the bearer token, refreshes on 401 and converts failures to `ApiError`. Do not add auth
code anywhere else.

## 3. Hooks (`src/features/<feature>/hooks.ts`)

```ts
export function useThings() {
  return useQuery({ queryKey: queryKeys.things, queryFn: thingsApi.list });
}
export function useCreateThing() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => thingsApi.create(name),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.things }),
  });
}
```

- Invalidate **every** query the mutation affects (watchlist changes also affect the dashboard, analytics and
  prices).
- Use `placeholderData: keepPreviousData` for parameterised views (periods, pages), so the UI does not flash.
- For market data, poll only while `dataStatus === 'PENDING'` (see `pollWhilePending` in `features/stocks/hooks.ts`).

## 4. Page or component

- Default-export pages from `features/<feature>/<Name>Page.tsx`; register them in `app/router/lazyPages.ts` and
  `app/router/routes.tsx` (under `/app` for authenticated pages), and in `app/layout/navigation.ts` if they need a
  sidebar entry.
- Structure: `<PageHeader title subtitle actions />`, then the states:
  - loading → `CardGridSkeleton` / `TableSkeleton` / `ChartSkeleton`
  - error → `<ErrorState title="Unable to load …" error={query.error} onRetry={() => void query.refetch()} />`
  - empty → `<EmptyState icon title description action />` with a useful next step
  - pending market data → `DataStatusChip` or an info `Alert` explaining that data is being collected
- Numbers: `formatCurrency`, `formatPercent`, `formatDate`, `formatTime` from `utils/format.ts`; signed changes with
  `<ChangeIndicator value={ratio} />`. Never calculate financial values in the component.
- Forms: React Hook Form + `zodResolver(schema)` + `FormTextField`. Map backend errors: known `code` →
  `setError(field, …)`, `apiError.fieldErrors` → matching fields, otherwise an `Alert`. Disable submit while
  submitting (`<Button loading={isSubmitting}>`).
- Feedback after mutations: `useNotify()` (with an `Undo`/`View` action where useful). Destructive actions need
  `ConfirmDialog`.
- Accessibility: every control has an accessible name (MUI 9: input attributes via `slotProps.htmlInput`), headings
  in order, `aria-label` on tables and charts, keyboard-operable rows and links. On small screens tables become
  cards (`useMediaQuery(theme.breakpoints.down('sm'))`).
- Analytics, forecasts and AI output end with `<Disclaimer />`.

## 5. Charts

Build the ECharts option in a pure function (`<name>ChartOption.ts`) from data plus `useChartTheme()`, render with
`<LazyEChart option height ariaLabel />`, and unit-test the option builder (series data, axes, tooltip text).

## 6. Tests (`*.test.tsx` next to the code)

```ts
it('creates a thing', async () => {
  let body: unknown;
  server.use(http.post(api('/things'), async ({ request }) => {
    body = await request.json();
    return HttpResponse.json({ id: 't1', name: 'Alpha' }, { status: 201 });
  }));
  const { user } = renderApp('/app/things');

  await user.click(await screen.findByRole('button', { name: 'Create thing' }));
  // …
  expect(body).toEqual({ name: 'Alpha' });
});
```

- Add default handlers for new endpoints to `src/test/handlers.ts` (specific paths before parameterised ones) and
  fixtures to `src/test/fixtures.ts`. Unhandled requests fail tests.
- Cover: happy path, one error path (`apiError(status, code, message)` helper), empty state, and any
  ownership-related 404 rendering.
- Use `findBy*` for anything async (lazy pages, queries, debounced search); query by role and accessible name.

## 7. Verify

```bash
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
```
