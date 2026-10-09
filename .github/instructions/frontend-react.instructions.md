---
applyTo: "frontend/src/**/*.ts,frontend/src/**/*.tsx"
---

# Frontend rules (details: frontend/AGENTS.md)

- Feature-oriented placement: `features/<feature>/` for pages, hooks and feature components; shared building blocks
  in `components/`, `hooks/`, `utils/`.
- HTTP only through `api/<area>Api.ts` functions using `httpClient`; never import Axios in components. Query keys
  from `api/queryKeys.ts`; invalidate affected queries after mutations.
- Never handle tokens outside `api/httpClient.ts` and `features/auth/`. Access tokens stay in memory, never in
  web storage.
- Do not compute financial figures; format backend values with `utils/format.ts`. Use `ChangeIndicator` for
  signed changes (arrow + sign, not colour alone).
- Every data view needs loading (skeleton), empty, error (`ErrorState` with retry) and, for market data, pending
  states.
- Types: no `any`. Mirror backend DTOs in `models/`.
- Accessibility: labelled inputs (MUI 9: `slotProps.htmlInput` for input attributes), semantic headings and
  landmarks, `ariaLabel` on charts.
- Tests: Vitest + Testing Library + MSW through `renderApp(path)`. Query by role and name, use `findBy*` for async
  UI, and override handlers with `server.use(...)`.
