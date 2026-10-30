# Decisions log

Append-only. Newest at the bottom. Format: decision, why, rejected alternatives.

## 2026-10-15

### D1. Data source: Blitzortung live websocket first
- **Why:** global, push-based, rich fields; project is local-only and educational. Switch if access becomes an issue.
- **Caveat:** Blitzortung ToS forbids automated download; mitigated by one connection, backoff, no republishing, attribution, replay sample for dev.
- **Rejected:** NOAA GOES GLM as primary (Americas only, NetCDF parsing; kept as fallback), Xweather (free tier 100 km / 5 min), OpenWeather stream (pricing unclear), MTG LI (registration, Europe only).

### D2. Fallback source: NOAA GOES-19/18 GLM
- Public S3 `noaa-goes19` / `GLM-L2-LCFA/YYYY/DDD/HH/`, file every 20 s, ~30 s latency (checked 2026-10-15). Stretch goal.

### D3. Pluggable `StrikeSource` trait
- Implementations: `BlitzortungSource` (live), `ReplaySource` (recorded `.jsonl`, dev/tests), later `GlmSource`. Selected via config.

### D4. Rebuild a slim Bootzooka from a clean sheet
- **Why:** learning value; see each piece arrive.
- **Dropped:** users/auth/api keys, password reset, email, Helm, OTEL (stretch), macwire.

### D5. Manual dependency wiring
- Plain constructor wiring in `Dependencies.scala`. **Rejected:** macwire (hides wiring from a beginner).

### D6. Backend stack
- Scala 3, sbt 2.0.10, JDK Temurin 25 LTS (system Java 27 is non-LTS), Tapir netty-sync, Ox, sttp client4, jsoniter-scala, pureconfig, logback, ScalaTest.
- **Rejected:** scala-cli as main build (fine for experiments only), cats-effect/ZIO.

### D7. Browser transport: SSE
- One-way data, auto-reconnect, simple. **Rejected:** WebSockets.

### D8. Map: MapLibre GL JS ≥ 6.12 + react-map-gl/maplibre ≥ 8.1.2 + OpenFreeMap dark
- WebGL heatmap/circle layers, no API key. Strikes kept in a ref buffer, `setData` throttled (~500 ms).
- **Rejected:** Leaflet (too slow at volume), OpenLayers (weak React story), CARTO basemaps (now need key), mapcn (reference only).

### D9. Frontend: Vite + React + TS + Tailwind v4 + shadcn/ui (Radix base), pnpm
- `shadcn init -t vite -b radix` (Base UI is default since 2026-07; most examples assume Radix).

### D10. Persistence: in-memory first, Postgres later
- Postgres + Flyway + Magnum as a late phase.

### D11. IDE: IntelliJ IDEA Ultimate + Scala plugin
- Covers Scala and TS in one window. **Rejected:** VS Code + Metals (viable, but user has JetBrains).

### D12. Repo & collaboration
- Fresh git repo, branch `main`, no remote until requested.
- Code comments: short one-liners only. Every decision logged here in the same turn.

### D13. Commit messages
- Single line, starts with a capital letter, no type/area prefix.
- One sentence: no trailing period. Several sentences: normal punctuation.

## 2026-10-16

### D14. Scala 3.9.0 (LTS line)
- Pairs with JDK 25 LTS; same as Bootzooka. **Rejected:** 3.10.0 (newest, non-LTS).

### D15. Dev restart via custom `reStart`/`reStop` tasks on `bgRun`
- sbt-revolver has no sbt 2 release; Bootzooka uses the same approach.

### D16. Simplified HTTP layer
- Errors as `(StatusCode, Error_OUT)` on a shared `baseEndpoint`; Swagger generated from server endpoints directly.
- **Rejected for now:** Bootzooka's `Fail` ADT and separate docs endpoint list (added if/when needed).

### D17. Project naming and shared IDE run configs
- sbt names: root `ride-the-lightning`, backend `backend` (IntelliJ module `ride-the-lightning.backend.main`).
- IntelliJ run configs committed in `.idea/runConfigurations/`: `Backend` (run/debug) and `Backend (watch)` (`~backend/reStart`).

## 2026-10-17

### D18. Recordings stay local
- Live recordings go to `data/` (gitignored); only a 3-frame fixture is committed for decoder tests.

### D19. Reconnect policy
- Ox `retry`, exponential backoff 1 s → 1 min with jitter, infinite; random server, never the same one twice in a row.
- A session only ends by exception (closed socket or failed upgrade), so every end triggers a retry.

### D20. Replay semantics
- Recording is sorted by strike time, replayed in a loop at `speed`, with strike times shifted to "now" so time windows work.

### D21. Deduplicate strikes in processing
- Blitzortung sends the same strike once per overlapping region; dedupe in Phase 3, not in the source.

### D22. Deduplication rule
- Same strike if |Δt| < 1 ms and |Δlat|, |Δlon| < 0.02°, remembered for 1 min after arrival.
- **Why:** in a 200-message recording, 37 exact repeats (same strike, other region) and 17 re-solutions µs apart and < 1 km; distinct strokes are ms apart.

### D23. No hub until there are several consumers
- Phase 3: one ingest fork writes straight to the window. Fan-out (broadcast to SSE clients) arrives with Phase 4.

### D24. Strike window
- Last 60 min (config `strikes.window`) by strike time, immutable `Vector` in an `AtomicReference`: one writer, lock-free readers.

### D25. Stats computed on request
- Pure `Stats.compute` over a window snapshot. **Rejected for now:** incremental aggregates (unneeded at a few strikes/s).

### D26. Config durations
- Durations in HOCON style (`60 minutes`), read as `FiniteDuration`, converted to `java.time.Duration` in code. A test loads the real `application.conf`.

### D27. API type naming and domain/response split
- API types are `XResponse` / `XRequest` (PascalCase, no `_IN`/`_OUT`); nested parts keep plain names (`MinuteCount`, `Hotspot`).
- Domain types aren't returned directly: `StrikeResponse.from(strike)`; lists are wrapped in a response object (`RecentStrikesResponse`).
- **Rejected:** Bootzooka's `_OUT` suffix (house style, not a Scala convention); returning the domain `Strike`.

## 2026-10-18

### D28. SSE fan-out
- `StrikeBroadcaster`: one bounded queue (1000) per subscriber; `publish` uses non-blocking `offer`, so a full queue drops the strike for that client only (counted, logged on disconnect).
- Subscription lifetime = flow lifetime (`try`/`finally`), so disconnects clean up automatically.

### D29. Stream format
- `GET /api/v1/strikes/stream`: `strike` events (StrikeResponse JSON) for each new deduplicated strike, `heartbeat` every 15 s.
- No event ids or replay: clients load `/strikes/recent` first, then follow the stream.

### D30. Endpoint capabilities and tests
- `HttpApi[R >: OxStreams & WebSockets]`; REST endpoints stay `Any` so the fast stub tests keep working (`HttpApi[Any]`).
- SSE is tested against a real server on a random port.

### D31. Frontend scaffold
- `ui/` from `create-vite` (react-ts), Vite 8, React 19, TypeScript 6, pnpm.
- Linting with **Oxlint** (template default). **Rejected:** ESLint (slower, more config). shadcn-generated `src/components/ui/**` is excluded from lint.

### D32. shadcn/ui setup
- `shadcn init -t vite -b radix -p nova`: Radix base, Nova preset (Lucide icons, Geist font), neutral palette, CSS variables. Dark mode via `class="dark"` on `<html>`.

### D33. Dev networking
- Vite dev server on :5173 proxies `/api` to the backend on :8080, so the browser never makes cross-origin calls in dev (SSE included).

### D34. API types in the UI
- Hand-written TS types mirroring `*Response` for now. **Deferred:** OpenAPI → TS codegen (Phase 7, optional).

### D35. Map stack
- `maplibre-gl` 6.14.0 + `react-map-gl` 8.1.3, pinned to exact versions; OpenFreeMap `dark` style.

### D36. MapLibre worker under Vite
- MapLibre v6 resolves its worker relative to its own file, which breaks under Vite (dev pre-bundling and prod hashing).
- Fix: import the worker with `?worker&url` (ES worker format) and call `setWorkerUrl` inside a lazy `import('maplibre-gl')` passed as `mapLib`, so MapLibre stays in its own chunk.
- **Rejected:** `optimizeDeps.exclude` (fixes dev only, not the production build).

### D37. Live strikes in the UI
- `StrikeBuffer` outside React state (15-min window, deduped by time+lat+lon); MapLibre gets a snapshot via `setData` every 500 ms; React re-renders only status and a once-a-second count.
- `/strikes/recent` is reloaded on every stream (re)connect; manual retry every 5 s when `EventSource` gives up on an error response.

### D38. Frontend tests
- Vitest for pure logic first (`strikeBuffer.test.ts`); component tests later if needed.

### D39. Strike animations
- Fade-out is data-driven: opacity and heatmap weight reach 0 at the end of the 15-min window, so eviction is invisible.
- Arrival flash (~1.5 s) is a CSS animation on a short-lived MapLibre `Marker`, only for new strikes from the live stream (not the `/strikes/recent` backfill), capped at 100 at once, hidden under `prefers-reduced-motion`.
- **Rejected:** animating a GeoJSON source per frame (`setData` ~30×/s flooded MapLibre's worker and starved basemap tiles).

### D40. One connection status; foldable stats panel
- The header badge is the only connection indicator, driven by the SSE stream status (`/health` polling removed from the UI; the endpoint stays for ops). The map shows just the strike count.
- The stats card folds into a 3.5rem rail; the preference is kept in `localStorage` (`rtl.statsOpen`).

## 2026-10-23

### D41. Frontend structure
- `src/{app, api, features/<feature>, shared}`; inside a feature: `containers/` (smart: hooks, stores) and `components/` (presentational: props, callbacks), plus `hooks/`, `stores/`, `lib/`, `utils/`, `constants/`, `types/` on demand. Rulebook: `docs/frontend-conventions.md`.
- Dependency rule `api ← shared ← features ← app`; features never import each other or `app/`. Enforced by Oxlint per-folder `no-restricted-imports`.

### D42. Frontend state
- Server state: react-query. Client state: one Zustand store per concern (`*.store.ts`), `persist` for UI preferences. High-frequency data (map strikes) stays in plain objects outside React.
- **Rejected:** hooks + Context only (more boilerplate, re-render care).

### D43. Generated API client
- Orval generates `src/api` (react-query hooks + fetch, kebab-case files, no index files, plain response bodies) from `/api/v1/docs/docs.yaml`; committed; `pnpm api:generate` with the backend running. Replaces the hand-written types (supersedes D34).
- The SSE endpoint is tagged `stream` and excluded from generation (browsers use `EventSource`).

### D44. Response contract: collections always present
- jsoniter's default omits empty collections, and Tapir marks them optional; `rtl.http.ResponseContract` keeps `[]` in JSON and marks collections required, so generated types are non-optional. Imported where response types are declared.

### D45. UI formatting and editor settings
- Prettier: single quotes, semicolons, width 160, LF; plugins sort imports (third-party, then `@api` → `@shared` → `@features` → `@app`, then `./`) and Tailwind classes.
- `.editorconfig` gets a `ui/` section matching Prettier (width 160, single quotes).

### D46. UI linting
- Oxlint: kebab-case filenames, no barrel files, no import cycles, no default exports in `src/`, `type` over `interface`, `import type`, hook dependency checks, layer boundaries, no `../` imports.
- Stylelint with `stylelint-config-standard` for CSS; oklch notation rules off (shadcn-generated tokens), Tailwind at-rules allowed.
- Feature CSS is colocated with the code that uses it; `styles/globals.css` holds only Tailwind and theme tokens.

### D47. Exact npm versions
- Every dependency pinned to an exact version; `saveExact: true` in `pnpm-workspace.yaml`. Only `esbuild` may run install scripts (`allowBuilds`).

### D48. Stats panel
- `useGetStats` polled every 5 s with react-query; the poll keeps running while the panel is folded (cheap, and the card has fresh data when unfolded). On error the last good data stays, with a header note; no data + error shows "Stats unavailable".
- Per-minute chart: shadcn `chart` (Recharts 3.8.0, exact), bars in `--chart-1`, animation off so 5 s refreshes don't re-grow bars.
- Regions are shown as "Region {id}" until names arrive in Phase 8.

### D49. Fly-to across features
- `App` holds `MapFocus | null` (`shared/types/map-focus.ts`) and wires the stats `onHotspotSelect` to `LiveMap`; features stay independent. `requestId` increments per request so repeated picks fly again. `flyTo` 1.6 s at zoom 6, `jumpTo` for reduced motion, deferred until the map loads.

### D50. Name places by geography, not Blitzortung regions
- Blitzortung `region` ids are overlapping processing areas: the same strike arrives once per region and dedupe keeps the first, so per-region counts are arbitrary. The map's own name table matches our data only for ids 0–5 (Unknown, Europe, Oceania, North America, Asia, South America); higher ids don't match where strikes fall.
- Instead the backend classifies each strike by coordinates against Natural Earth (public domain) country and ocean/sea shapes; stats show top places and hotspots get a place name.
- Supersedes the D48 "Region {id}" bullet; done in Phase 7.
- API: `regions` is removed; `places` lists `{ name, kind: country | sea, count }`, last 10 min, top 10, busiest first; `Hotspot.place` is the most common place in that cell. Points more than 3° from any shape are "Open ocean".
- Data: Natural Earth 1:50m countries (`NAME_EN`, full English names rather than abbreviated map labels) and marine polygons, bundled as `geo/places.json.gz` by `scripts/build-places.py`, pinned to a natural-earth-vector commit, coordinates rounded to 3 decimals.
- **Rejected:** mapping the official region names (partial and unreliable); continents only (too coarse).

### D51. Stats panel always shown; phone layout
- The fold/rail is removed (desktop and phone), together with its persisted store; supersedes the D40 fold bullet and the D48 note about polling while folded.
- Below `lg` map and stats stack and the page scrolls (map `60dvh`, min 20rem; stats at natural height). From `lg` the page fits the viewport and only the stats content scrolls. Picking a hotspot scrolls the page back to the map.
- The map attribution starts collapsed behind MapLibre's (i) button.
- The UI treats 12 s without a strike or heartbeat as a dead stream and reconnects (Vite's dev proxy keeps the browser's SSE connection open after the backend dies, so the badge never went offline); the backend heartbeat drops from 15 s to 5 s to keep detection quick.
- The map's strike-count pill is removed (the stats card already shows counts); the header badge reads just `online` / `offline`, with the backend version as its tooltip.
- **Rejected:** keeping the fold with chevrons on phones; a bottom sheet (more code for little gain).

### D52. Natural Earth downloaded by the backend
- The backend downloads the two Natural Earth 1:50m GeoJSON files (same pinned commit) into `data/geo/` (gitignored, `geo.cache-dir`) when they're missing, then reads them from disk on every start; tests use the same cache. Supersedes the D50 bundling bullet: no Python script, no bundled `places.json.gz`, no coordinate rounding.
- Loaded at startup, before ingest, because every strike is classified as it arrives; a failed download (3 retries) stops the start with the error.
- **Rejected:** loading on the first request (strikes arriving before it would have no place); bundling a preprocessed file built by a Python script (an extra toolchain).

## 2026-10-25

### D53. Stations used
- `Strike.stations` counts `sig` entries with `status & 4` (Blitzortung's "signal used for the computation" bit) instead of `sig.size`. The feed lists at most 40 stations per strike, used or not, so the count is a lower bound when the list is full. In the sample: 8–32 used, never fewer than 8 (Blitzortung's minimum for a fix).
- **Rejected:** `sig.size` (capped at 40 and includes unused stations); showing both "reported" and "used" (more UI, little value).

### D54. Timing-spread filter
- `mds` is Blitzortung's "maximal deviation span" in ns: how far the stations' timings disagree with the computed position, lower is better. There is no official quality threshold, so the filter is our own cut: `0 < mds ≤ 10 µs` (`TIGHT_MDS_NS`), an on/off switch, off by default, named by what it does ("timing spread"), not "accurate strikes". A missing `mds` (0) doesn't pass.
- Why 10 µs: the sample tops out near 15 µs (likely a server-side cut, so 15 µs would hide nothing); 10 µs keeps about two thirds; 7.5 µs drops more than half.
- **Rejected:** 15 µs and 7.5 µs; a quantile threshold that moves with the data (hard to explain); an `mcg` (station-geometry) filter (a second setting, maybe later); strike-level `status` (undocumented).

### D55. Where the filter applies
- `GET /stats?maxMds=<ns>` filters on the server (all window-based numbers, plus `maxMds` echo and `filteredOut`); the map filters on the client by `StrikeResponse.mds`. `/strikes/recent` and the SSE stream stay unfiltered, so toggling needs no reconnect and ingest/Blitzortung traffic is unchanged.
- **Rejected:** a filtered stream with a per-client parameter (reconnect per toggle); computing stats in the browser (it holds only 15 min, logic duplicated); an always-on server filter (hides data on an unverified threshold).

### D56. Detection stats
- `StatsResponse.detection`: nearest-rank p50/p90/p99 of stations used, delay (s) and `mds` (ns) over the last 10 minutes (same window as places and hotspots), after the filter, omitted when empty. Delay is publication lag (two groups in the sample: ~3–6 s and ~20–36 s), so p90/p99 matter.
- Delay and `mds` percentiles skip 0, which the decoder uses for a missing field.
- **Rejected:** a stations histogram (buckets tuned on 200 capped strikes); interpolated percentiles; a 60-minute window.

### D57. 0.5° hotspots
- Hotspot cells are 0.5° (`floor(x / 0.5) * 0.5`, centre +0.25), ties ordered by latitude then longitude; coordinates shown with 2 decimals.
- **Rejected:** keeping 1° (merges neighbouring storm cells); H3 (stretch goal).

### D58. Filter state and toggle
- A Zustand `persist` store in `shared/stores` (`rtl.strikeFilter`, a boolean) read by the map, the stats panel and the header; the switch sits in the app header (switch only below `sm`, label from `sm`). Labels derive from `TIGHT_MDS_NS`.
- **Rejected:** `useState` in `App` (not persisted, prop drilling); a store inside one feature (cross-feature import); the toggle in the stats card (below the map on phones).

## 2026-10-28

### D59. Packaging with sbt-native-packager
- A separate `docker` sbt project (as in Bootzooka) depends on `backend`, runs `pnpm install --frozen-lockfile` + `pnpm build` as a resource generator and puts `ui/dist` into its jar under `webapp/`. Built with `sbt docker/Docker/publishLocal` → `ride-the-lightning:latest` on `eclipse-temurin:25-jre`, JVM flags from `.jvmopts` passed through the start script. The project isn't aggregated by `root`, so `compile` and `testFull` never run pnpm.
- **Rejected:** a hand-written multi-stage Dockerfile (plan and Bootzooka use native-packager); copying the UI into `backend` resources (pnpm on every compile, stale UI on :8080 in dev); a fat jar (sbt-assembly, no image).

### D60. Backend serves the built UI
- tapir-files `staticResourcesGetServerEndpoint` on `/`, after the API, from `webapp/` on the classpath. No `index.html` fallback: the UI has no client-side routes, and a fallback would answer unknown `/api/v1/*` paths with 200 HTML. Unknown paths get the files endpoint's 404 with an empty body. Same origin, so no CORS or proxy in the image; dev keeps Vite + proxy (D33).
- **Rejected:** SPA fallback (see above); nginx in front (a second container for static files).

### D61. Container runtime settings
- The image sets `HTTP_HOST=0.0.0.0` and `GEO_CACHE_DIR=/opt/docker/data/geo`; that directory is created in the image owned by the app user (uid 1001), so a new named volume inherits a writable owner. `docker-compose.yml`: one service on :8080, named volume `geo`, `./data` mounted read-only at `/recordings`; live feed by default, `INGEST_SOURCE=replay` opts into the recording.
- **Rejected:** bind-mounting `data/geo` (host uid ≠ 1001, not writable); shapes baked into the image (D52 downloads them on first start); replay as the compose default (the image is the real app; the recording stays local, D18).

### D62. CI on GitHub Actions
- One job on `ubuntu-24.04`: `jdx/mise-action` installs the tools from `.mise.toml`; caches coursier/sbt and `data/geo`; runs `sbt testFull`, UI `lint`, `format:check`, `test`, then `sbt docker/Docker/publishLocal` (builds the UI and the image, pushes nothing). Inert until a remote exists (D12).
- **Rejected:** `setup-java` + `setup-sbt` + `setup-node` (duplicates `.mise.toml`); publishing the image (local-only project); several parallel jobs (each repeats tool setup for a small build).

### D63. Basic auth in the backend
- Optional HTTP basic auth (`BASIC_AUTH_USER` / `BASIC_AUTH_PASSWORD`) on every endpoint, Swagger and the UI files, as a Tapir security input mapped to `Unit`; a wrong password decodes as missing, so Tapir answers 401 with the challenge and the browser asks again. `/api/v1/health` stays open for platform checks. Constant-time comparison. `REQUIRE_BASIC_AUTH=true` refuses to start without credentials, so a forgotten secret can't leave a public deployment open. Off by default for local dev.
- **Rejected:** a Caddy/nginx sidecar (a second process to configure); Cloudflare Access (needs a domain on Cloudflare); `fly proxy` only (no URL to open from a phone); a password check in the UI (anyone can call the API directly).

### D64. Hosting on Fly.io
- Private deployment for the owner only, behind basic auth (D63), so the Blitzortung feed isn't republished. One `shared-cpu-1x` 1 GB machine in `ams`, never stopped (`auto_stop_machines = "off"`, deploy with `--ha=false`): ingest must keep running, and a second machine would open a second Blitzortung connection. Fly's remote builder builds the image from `docker/Docker/stage`, so no local Docker is needed. No volume: the shapes (~4 MB) download on each machine start. Heap = 60% of the container's memory (`-XX:MaxRAMPercentage=60`). flyctl pinned in `.mise.toml`.
- **Rejected:** free tiers that sleep (Render, Koyeb: ingest stops); free VMs (GCP e2-micro, Oracle: more admin, Oracle reclaims idle instances); a VPS (cheaper per GB, but OS and Docker upkeep); a Fly volume (root-owned, the app runs as uid 1001); a public deployment (Blitzortung terms).

### D65. Deploy from CI
- The last step of the CI job runs `fly deploy` on pushes to `main`, after tests, UI checks and the image build pass, with `--local-only`: flyctl builds the staged context with the runner's Docker (layers cached from the check build, so it's the image that was checked) and pushes it to Fly's registry. Locally, without Docker, `--remote-only` builds on Fly's builder instead. Auth is an app-scoped Fly deploy token in the `FLY_API_TOKEN` repo secret. Runs are serialized per branch (`concurrency`), so two pushes can't deploy over each other. Pull requests run the checks only.
- **Rejected:** `--remote-only` in CI (builds the image a second time on Fly); dropping the image check for Fly's build only (PRs would no longer check the image); a separate deploy job (repeats tool setup and the UI build); an org-wide Fly token (more access than deploying one app); pushing the image to ghcr.io first (Fly's remote builder already stores it in its registry).

### D66. CI as separate jobs
- Supersedes the single job of D62/D65. Jobs: `Backend tests` and `UI checks` in parallel, then `Docker image` (on `main` it also pushes `registry.fly.io/ride-the-lightning:<sha>`), then `Deploy to Fly.io` on `main` (`fly deploy --image`, no build). Each job installs only the mise tools it needs. The run page shows each stage and where it failed; the image is built once and the deploy ships exactly that image.
- **Rejected:** one job with named steps (one box on the run page); a deploy job that rebuilds (`--local-only` or `--remote-only`, a second build); ghcr.io (Fly would need registry credentials to pull).

## 2026-10-29

### D67. CI test reports and action versions
- `Backend tests` publishes sbt's JUnit XML (`backend/test-reports/TEST-*.xml`) with `mikepenz/action-junit-report@v6` as a totals table plus every test grouped by suite (`include_passed`, `group_suite`; without them a green run shows an empty "No test annotations available" table) in the run summary, also when tests fail (`!cancelled()`), next to Vitest's built-in summary for `UI checks`. `annotate_only`, so no check run and no extra token permissions.
- Actions on Node 24 to clear GitHub's Node 20 deprecation warnings: `actions/cache@v6`, `jdx/mise-action@v5` (its new 24 h minimum release age applies to the mise binary only; tool versions stay pinned in `.mise.toml`).
- **Rejected:** `dorny/test-reporter` (creates a check run, needs `checks: write`); a hand-written XML-to-Markdown script (more code to maintain).

### D68. Phase 10 scope: simple persistence and map playback
- Every deduplicated live strike is stored and kept 7 days. On start the 60-minute window is refilled from storage. The map gets a Playback mode (presets 1 h / 6 h / 24 h / 7 d ending now, plus a slider); Live stays the default. Long ranges are thinned server-side with a "1 in n strikes" note. The stats panel stays live during playback, with a small "Live" badge next to "Stats". Supersedes the plan's "hourly aggregates": D25 (stats on request) still holds and playback reads raw rows.
- **Rejected:** hourly aggregate tables (no reader yet); history stats in the stats panel (new backend work); a custom date-time picker (presets plus the slider reach any moment; the API takes any range); storing raw `sig[]` (~40× the row).

### D69. SQLite on a Fly volume
- Supersedes D10's Postgres. One file, `history.db` under the app's data dir, via `sqlite-jdbc` 3.53.4.0, Magnum 1.3.1 and Flyway 13.10.0 (`flyway-core` has SQLite support built in; no `flyway-database-*` module). WAL journal, `synchronous=NORMAL` and `busy_timeout` 5 s on every connection: one writer (the batch writer) and concurrent readers.
- `SQLiteDataSource` without a pool: opening a SQLite connection is a file open, so a pool only adds a dependency and settings to explain; the pragmas come from `SQLiteConfig` on each new connection.
- A Fly volume has the same durability as a self-run Postgres (single host, daily snapshots) for ~$0.15-0.45/month. No Docker for local dev and no embedded Postgres in tests: tests use a real, migrated temp file.
- **Rejected:** self-run Postgres on a second Fly app (~$5/month, image upgrades); Fly Managed Postgres (~$38-41/month); Neon/Supabase free tiers (compute hours run out, 500 MB, no backups); LiteFS/Litestream (not needed for now); HikariCP (adds nothing for a local file).

### D70. Strike table
- One `strikes` table: the `Strike` fields without `place`, time as `time_us INTEGER` (epoch microseconds; SQLite has no timestamp type), `delay_seconds REAL` (SQLite `REAL` is 8 bytes, so 5.2 stays 5.2), one index on `time_us`, no primary key. Places are located again on refill. Strike times are truncated to µs when parsed (`BlitzortungMessage.toStrike`, also replay), so dedupe, recording, window, storage and JSON all carry the same instant and the UI buffer doesn't show a strike twice after a restart.
- **Rejected:** a primary key (no reader); `UNIQUE(time, lat, lon)` (copies of a strike differ, D22); stored place name/kind (stale when shapes change); nanosecond time (Blitzortung timing is µs-grade); ISO text time (bigger, slower to compare).

### D71. Storing never blocks ingest
- Ingest `offer`s each new strike to a `StrikeWriter`: an `ArrayBlockingQueue` of 10 000; a full queue drops and counts the strike. A fork takes a strike, drains up to 499 more, writes them in one transaction (`batchUpdate`) and sleeps 1 s, so each transaction gathers about a second. A failed batch is logged, counted as dropped and followed by a 5 s pause; it isn't retried. No flush on shutdown (≤ 1 s lost). `stored` and `store dropped` go in the minute log.
- **Rejected:** backpressure into the ingest loop (a slow disk would freeze the live map and the Blitzortung socket); Ox `groupedWithin` (hidden internal buffers make the capacity and the drop test non-deterministic); retrying failed batches (state, double inserts); one transaction per strike.

### D72. History is optional; the live view never depends on it
- `history.enabled` (`HISTORY_ENABLED`, default true) and `history.file` (`HISTORY_FILE`, default `data/history.db`). If the file can't be opened or migrated at startup, the app logs an ERROR and runs live without history (no playback, writes or refill); a failing refill is logged too.
- Writes, refill and retention run only with the live source. With `INGEST_SOURCE=replay` the file is opened and migrated but only read by `/history/*` ("Replay mode: history is read-only"), because replay strike times are fake (D20). This lets playback be developed offline against a stored file.
- Restored strikes from the last minute seed the deduplicator, so late region copies after a quick restart are still dropped.
- **Rejected:** refusing to start without history (a full outage for a nice-to-have); no database in replay mode (playback couldn't be developed offline).

### D73. Retention
- A fork deletes rows older than `history.keep` (7 days) at start, then every hour, with the cutoff from the app `Clock` (never `now` in SQL). Errors are logged and the loop goes on. SQLite reuses the freed pages.
- **Rejected:** a daily delete (bigger bursts, up to 8 days kept); `VACUUM` (rewrites the file; the size is stable anyway).

### D74. History API
- Tag `history`, behind basic auth like the rest. `GET /api/v1/history/range` → `{enabled, oldest?, newest?, keepDays}`. `GET /api/v1/history/strikes?from&to` (ISO instants) → `{from, to, step, strikes: [{timeMs, lat, lon, mds}]}`, half-open `[from, to)`, oldest first. 400 for `to <= from`, a span over `history.keep` or a malformed instant; 503 when history is off.
- At most `history.max-strikes` (100 000) rows: count first, then `step = max(1, ceil(n / max))` and every `step`-th row by `row_number()` in time order, which keeps the relative density.
- **Rejected:** full `StrikeResponse` rows (~2× larger); paging or streaming chunks (async loading during the animation); a 24 h max span (the owner wants 7 days); server-side gridding (a second rendering path).

### D75. Playback in the map
- A mode inside `live-map`, so the map instance, layers and camera stay (the map is never remounted or keyed by mode). A plain Zustand store (`playback.store.ts`, no persist: the map always opens live; mode is `range !== null`). A bar below the map in the same card, shown only when `/history/range` says `enabled`: `Live | Playback`, play/pause, a time slider, presets 1 h/6 h/24 h/7 d ending now (clamped to the oldest stored strike; a preset is disabled when a shorter one already covers everything stored; Playback is disabled while nothing is stored), speeds 1 min/s, 10 min/s and 1 h/s with 15 min, 1 h and 3 h fading trails. Picking Playback starts 6 h (or less, if that's all there is) at 10 min/s, playing.
- `StrikeMap` takes `frames` (`snapshot`, `subscribe`, optional `onArrival`). Live redraws every 500 ms as before; playback redraws only when the playhead moves. One clock: a 250 ms ticker advances the store by real time × speed, with the gap clamped to 1 s (throttled background tabs). `PlaybackControls` is the only component that re-renders per tick; the "Playback" pill over the map is static.
- `StrikeTimeline` slices the time-sorted response by binary search (never evicts, so scrubbing backwards works) and caps a frame at 10 000 features by taking every k-th strike, aligned to absolute indices so the same strikes stay picked. `mds` comes with each row, so the timing-spread filter works in playback.
- The SSE stream, the header badge and the stats panel stay live; the stats card shows a `Live` badge while the map is in playback. The live buffer is pruned every minute, so it doesn't grow while not drawn. No flashes in playback.
- History responses are dropped 30 s after leaving a range (`gcTime`; up to ~10 MB each); `/history/range` is refetched after a minute. The slider step scales with the span (1 min up to 6 h, 5 min for 24 h, 30 min for 7 d); the shadcn slider is edited to pass `aria-label`/`aria-valuetext` to its thumb. ToggleGroup handlers ignore Radix's `""` (clicking the pressed item). One `role="status"` line for loading, error (with Retry), empty and "Showing 1 in n strikes".
- **Rejected:** a separate `playback` feature (would import `live-map`, D49); reusing `StrikeBuffer` with a fake clock (it evicts and drops future strikes); controls overlaid on the map (collide with the attribution button, cover storms on phones); a second redraw timer for the map (beats against the ticker, redraws while paused); starting paused under reduced motion (playback is asked for, and the pause button is right there); per-mode heatmap weights (playback looks denser with longer trails; accepted).

### D76. Data volume and ownership
- One data dir, `/opt/docker/data`, holds the shapes (`geo/`) and `history.db`; the image sets `HISTORY_FILE=/opt/docker/data/history.db` next to `GEO_CACHE_DIR`, so the path doesn't depend on the working directory. Fly mounts a 1 GB volume `data` there (`[[mounts]]`, daily snapshots kept 7 days, created once by hand before the first deploy with it); compose uses a named volume `data` instead of `geo`. Supersedes D64's "no volume": the machine is now pinned to the volume's host in `ams`, and the shapes survive restarts.
- Ownership: Fly's docs don't say who owns a mount (a 2021 staff answer says it's chowned to the image's `USER`, but not when the entrypoint drops privileges). So the image no longer ends with `USER 1001:0`: `bin/docker-entrypoint.sh` starts as root, `chown`s the mount point to 1001 and `exec`s the start script through `setpriv --reuid=1001 --regid=0 --clear-groups` (util-linux, already in the Ubuntu 24.04 base of `eclipse-temurin:25-jre`). The JVM still runs as uid 1001 and stays the main process (`exec`), so SIGTERM still reaches it. The data dir is also created owned by 1001 in the image, so a new compose volume starts writable either way.
- **Rejected:** relying on Fly's undocumented chown (breaks silently if it changes); a one-off `chown` over `fly ssh console` (lost when the volume is recreated); running the JVM as root; `gosu`/`su-exec` (an extra package; `setpriv` is already there); `su`/`runuser` (they keep a root parent process in front of the JVM); a recursive `chown` on every start (only the mount point is root-owned; the app creates everything inside as 1001).

## 2026-10-30

### D77. Compile rewrites resources for IntelliJ
- After every `backend` compile, a small step deletes and recopies the current resources (`application.conf`, `logback.xml`, migrations) into `target/out/.../backend/classes`. IntelliJ's Application run configs start `rtl.Main` from that folder. When sbt 2 serves `compile` from its cache, it restores the folder as it was when the result was cached, old resources included (as symlinks into the read-only cache), and `copyResources` doesn't notice because its own cache says it already copied. A run from the IDE then failed with "Key not found: 'history'", while `sbt run` and the tests (which read resources elsewhere) were fine.
- **Rejected:** `dependsOn(copyResources)` before compile (the cache restore overwrites it; `copyResources` skips anyway); a "before launch" sbt task per IntelliJ config (easy to forget); running the backend from the IDE only via sbt tasks (no debugger on `rtl.Main`).

### D78. Timing-spread filter and station statistics removed
- Owner's call: the header's timing-spread switch, `/stats?maxMds` with its `maxMds` echo and `filteredOut`, and the client-side `mds` filter (map and playback) are gone, together with the `shared/stores` filter store. The detection section keeps only publication delay (`Detection(delaySeconds)`); the stations-used tiles, the "at most 40 stations" note and the timing-spread line are removed. `StrikeResponse` drops `stations` and `mds`, `PlaybackStrikeResponse` drops `mds`. Supersedes the filter parts of D54, D55, D58 and the stations/`mds` parts of D56.
- `Strike.stations` and `Strike.mds` stay in the domain model and the `strikes` table: the decoder still fills them and they cost little, so a later feature can use them without a migration.
- **Rejected:** dropping the columns too (a migration for no gain); keeping the filter but hiding the switch (dead code).

### D79. Flashes in playback
- Playback flashes strikes as the playhead passes them, like live arrivals: after each forward tick (only while playing, and not after a seek or a jump longer than one tick), `StrikeTimeline.arrivals` returns the strikes passed in `(previous, current]`, at most 4 per frame (`MAX_FLASHES_PER_FRAME`, so ≤ 16/s at the 250 ms tick), spread evenly over the interval. The existing cap of 100 flashes on screen and the reduced-motion rule apply to both modes. Supersedes "no flashes in playback" in D75.
- **Rejected:** a flash for every passed strike (hundreds per frame at 1 h/s); flashing the newest strikes only (bunches them at one edge of the frame); a separate flash style for playback.
