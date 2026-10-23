# Phase 5: frontend skeleton

## Run it
```bash
INGEST_SOURCE=replay sbt ~backend/reStart   # terminal 1 (or IntelliJ "Backend (replay)")
cd ui && pnpm install && pnpm dev           # terminal 2 (or IntelliJ "UI (dev)")
```
Open http://localhost:5173: dark shell, green `backend 0.1.0-SNAPSHOT` badge, placeholders for map and stats. Stop the backend and within 10 s the badge turns into `backend offline`.

Checks: `pnpm lint` (Oxlint), `pnpm build` (TypeScript check + production bundle into `ui/dist`).

## IntelliJ setup (once)
- Settings → Languages & Frameworks → Node.js: interpreter from mise (`~/.local/share/mise/installs/node/24…/bin/node`), package manager **pnpm**.
- Tailwind CSS support is bundled in Ultimate: class-name completion works in `.tsx` files.

## Files
> These were the Phase 5 paths. Since the frontend restructure (plan item 6a) the code lives under `src/app`, `src/features`, `src/shared` and the generated `src/api`; see `docs/frontend-conventions.md`.

```
ui/
  index.html                  page shell; class="dark" turns on the dark theme
  vite.config.ts              React + Tailwind plugins, @ alias, /api proxy → :8080
  components.json             shadcn settings (style, aliases, icon library)
  .oxlintrc.json              lint rules; ignores shadcn-generated components/ui
  src/main.tsx                entry: React root + react-query provider
  src/App.tsx                 layout: header, map card, stats card, attribution
  src/index.css               Tailwind import + shadcn theme variables (light and dark)
  src/api/client.ts           getJson: fetch /api/v1/..., throws with the backend's error message
  src/api/queries.ts          useHealth (polls every 10 s), useVersion
  src/api/types.ts            TS mirrors of HealthResponse, VersionResponse, ErrorResponse
  src/components/             AppHeader, BackendStatus, Attribution
  src/components/ui/          shadcn components (button, card, badge, separator, skeleton): your code now
```

## Concepts (with .NET / backend analogies)
- **Vite**: dev server with instant reload, plus the production bundler. `pnpm dev` ≈ `dotnet watch`, `pnpm build` ≈ `dotnet publish`.
- **pnpm**: package manager (≈ NuGet). `package.json` ≈ `.csproj` dependencies, `pnpm-lock.yaml` ≈ `packages.lock.json`.
- **Dev proxy**: the browser only talks to :5173; Vite forwards `/api/*` to :8080. Same origin means no CORS, and SSE works through it too. In production (Phase 9) the backend serves the built UI itself, so the same relative `/api` URLs keep working.
- **Tailwind**: styling with utility classes in markup (`flex h-14 items-center border-b px-4`) instead of separate CSS files. v4 needs no config file, just `@import "tailwindcss"`.
- **shadcn/ui**: not a dependency you upgrade; the CLI **copies component source** into `src/components/ui`, built on Radix primitives (accessibility, keyboard handling). You own and edit them. Theme colors are CSS variables in `index.css`.
- **react-query**: server state in the UI. `useQuery({ queryKey, queryFn })` caches results, tracks `isPending` / `isError` / `data`, retries and refetches (`refetchInterval`). It replaces hand-written `useEffect` + `useState` loading logic.
- **Components and hooks**: a component is a function returning JSX; hooks (`useHealth`) are functions that let components use state and effects. `BackendStatus` re-renders automatically when the health query changes.

## Try
1. `cd ui && pnpm dlx shadcn@latest add tooltip`, then wrap the status badge in a tooltip showing the Scala version.
2. Change `refetchInterval` to `2_000` and watch the Network tab in the browser dev tools.
3. Switch the theme: remove `class="dark"` from `index.html`.
