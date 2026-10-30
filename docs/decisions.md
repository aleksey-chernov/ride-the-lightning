# Decisions

What currently holds, by topic. Read it before proposing anything: don't re-propose a rejected option unless new facts appear.

- A new decision goes under its topic with the next free number (next: D82).
- A decision that changes an older one rewrites that entry, and what's gone moves to [Removed or replaced](#removed-or-replaced). `git log -p docs/decisions.md` has the full wording of every earlier version.
- `D<n>` are stable labels used across the docs and code. Entries merged later list all their numbers; gaps are fine.

## Project

### D1, D2, D3. Data sources (2026-10-15)
- Blitzortung live websocket first: global, push-based, rich fields. A pluggable `StrikeSource` trait has `BlitzortungSource` (live) and `ReplaySource` (recorded `.jsonl`, for dev and tests), chosen by config.
- Blitzortung's terms forbid automated download. We limit the impact: one connection, backoff, no republishing (private deployment, D64), attribution in the UI, developing against the recording.
- Fallback if access becomes a problem: NOAA GOES-19/18 GLM (public S3 `noaa-goes19`, `GLM-L2-LCFA/YYYY/DDD/HH/`, a file every 20 s, ~30 s behind). A stretch goal, not built.
- **Rejected as primary:** GLM (Americas only, NetCDF), Xweather (free tier 100 km / 5 min), OpenWeather stream (pricing unclear), MTG LI (registration, Europe and Africa only).

### D4, D5. Slim Bootzooka, wired by hand (2026-10-15)
- Rebuilt from a clean sheet to see each piece arrive, checking patterns against Bootzooka. Left out: users, auth and API keys, password reset, email, Helm, OTEL (stretch).
- Plain constructor wiring in `Dependencies.scala`. **Rejected:** macwire (hides the wiring from a beginner).

### D12, D13, D81. Repo and docs (2026-10-30)
- Branch `main`; code comments are short one-liners; concept explanations go in `docs/phase-N.md`.
- Commit messages: a single line, capital first letter, no type or area prefix. One sentence: no trailing period. Several: normal punctuation.
- This file is organised by topic and shows only what holds now (was: an append-only log, whose overrides were hard to follow).
- Phase docs are the record of what each phase built, so they keep their story, but everything a reader would act on (run steps, commands, paths, "Try" items) must work today. `README.md` is the short front page.
- **Rejected:** renumbering decisions (breaks references in docs, code and old commits); folding phase docs into one document (they're the learning trail).

### D11, D17. IDE and shared run configs (2026-10-16)
- IntelliJ IDEA Ultimate + Scala plugin: Scala and TypeScript in one window. **Rejected:** VS Code + Metals (viable, but JetBrains is already there).
- sbt names: root `ride-the-lightning`, backend `backend` (IntelliJ module `ride-the-lightning.backend.main`). Run configs are committed in `.idea/runConfigurations/`: `Backend`, `Backend (replay)`, `Backend (watch)`, `Backend tests`, `Backend tests (sbt)`, `UI (dev)`.

## Backend

### D6, D14, D15. Stack (2026-10-16)
- Scala 3.9.0 (LTS line), sbt 2.0.10, Temurin 25 LTS (system Java 27 is non-LTS), Tapir netty-sync, Ox, sttp client4, jsoniter-scala, pureconfig, logback, ScalaTest.
- `reStart`/`reStop` are custom tasks on `bgRun`: sbt-revolver has no sbt 2 release, and Bootzooka does the same.
- **Rejected:** scala-cli as the main build (experiments only); cats-effect/ZIO; Scala 3.10.0 (newest, non-LTS).

### D16. Simplified HTTP layer (2026-10-16)
- Errors are `(StatusCode, ErrorResponse)` on a shared `baseEndpoint`; Swagger is generated from the server endpoints. **Rejected for now:** Bootzooka's `Fail` ADT and a separate docs endpoint list.

### D26. Config durations (2026-10-17)
- HOCON style (`60 minutes`), read as `FiniteDuration`, converted to `java.time.Duration` in code. A test loads the real `application.conf`.

### D27, D44. API types (2026-10-23)
- `XResponse` / `XRequest` (no `_IN`/`_OUT`), nested parts keep plain names (`MinuteCount`, `Hotspot`). Domain types aren't returned directly (`StrikeResponse.from(strike)`), and lists are wrapped in a response object.
- jsoniter omits empty collections by default and Tapir marks them optional. `rtl.http.ResponseContract` keeps `[]` in the JSON and marks collections required, so generated types are non-optional. Import it where response types are declared.
- **Rejected:** Bootzooka's `_OUT` suffix (house style, not a Scala convention); returning the domain `Strike`.

### D77. Compile rewrites resources for IntelliJ (2026-10-30)
- After every `backend` compile, main and test, `freshResources` in `build.sbt` deletes and recopies the current resources into `target/out/.../classes` and `test-classes`, which is where IntelliJ's run configs read them. A cached sbt 2 compile restores those folders as they were when cached, old resources included (symlinks into the read-only cache), and `copyResources` doesn't notice. Symptoms were "Key not found: 'history'" in a Debug run and two `HttpApiTest` failures, only in IntelliJ.
- **Rejected:** `dependsOn(copyResources)` (the restore overwrites it, and it skips anyway); a "before launch" sbt task per config (easy to forget); IDE runs only via sbt tasks (no debugger on `rtl.Main`).

## Ingest and processing

### D18, D20. Recordings and replay (2026-10-17)
- Recordings go to `data/` (gitignored); only a 3-frame fixture is committed for decoder tests.
- Replay sorts by strike time and loops at `speed`, with times shifted to "now" so time windows work.

### D19. Reconnect (2026-10-17)
- Ox `retry`, forever, exponential backoff 1 s → 1 min with jitter; a random server, never the same one twice in a row. A session only ends by exception, so every end triggers a retry.

### D21, D22, D23. Deduplication (2026-10-17)
- Blitzortung sends a strike once per overlapping region, so processing dedupes (not the source): same strike if |Δt| < 1 ms and |Δlat|, |Δlon| < 0.02°, remembered 1 min after arrival. In a 200-message recording: 37 exact repeats and 17 re-solutions µs apart and < 1 km; distinct strokes are ms apart.
- One ingest fork does the work; fan-out exists only for SSE clients (D28).

### D24, D25. Window and stats (2026-10-17)
- Last 60 min (`strikes.window`) by strike time: an immutable `Vector` in an `AtomicReference`, one writer, lock-free readers. Refilled from history on start (D68).
- Stats are a pure `Stats.compute` over a window snapshot, per request. **Rejected:** incremental aggregates (unneeded at a few strikes/s).

### D50, D52. Places from geography, not Blitzortung regions (2026-10-23)
- Blitzortung `region` ids are overlapping processing areas: a strike arrives once per region and dedupe keeps the first, so per-region counts are arbitrary, and the official names fit only ids 0–5.
- The backend names each strike from its coordinates, using Natural Earth 1:50m countries (`NAME_EN`) and marine polygons. `/stats` lists `places` (`{name, kind: country | sea, count}`, last 10 min, top 10); `Hotspot.place` is the most common place in a cell. Beyond 3° from any shape: "Open ocean".
- The two GeoJSON files (pinned commit) are downloaded into `geo.cache-dir` when missing and read from disk on every start, before ingest; tests share the cache. A failed download (3 retries) stops the start.
- **Rejected:** mapping official region names (partial, unreliable); continents only (too coarse); loading on the first request (earlier strikes would have no place); a preprocessed bundled file from a Python script (another toolchain).

### D56, D57. Detection and hotspots (2026-10-25)
- `StatsResponse.detection`: nearest-rank p50/p90/p99 of publication delay over the last 10 minutes, skipping 0 (missing), omitted when empty. Delay has two groups (~3–6 s and ~20–36 s), so p90/p99 matter.
- Hotspots are 0.5° cells (`floor(x / 0.5) * 0.5`, centre +0.25), ties by latitude then longitude, shown with 2 decimals.
- `Strike.stations` counts `sig` entries with `status & 4` ("used for the computation"); the feed lists at most 40. It's stored, not served (D78).
- **Rejected:** a stations histogram; interpolated percentiles; a 60-minute window; 1° cells (merge neighbouring storm cells); H3 (stretch); `sig.size` as the station count.

## Streaming

### D7, D28, D29, D30. SSE (2026-10-18)
- SSE: one-way data, auto-reconnect, simple. **Rejected:** WebSockets.
- `GET /api/v1/strikes/stream`: a `strike` event (StrikeResponse JSON) per new deduplicated strike, a `heartbeat` every 5 s. No event ids or replay: clients load `/strikes/recent` first, then follow.
- `StrikeBroadcaster`: one bounded queue (1000) per subscriber, non-blocking `offer`, so a full queue drops the strike for that client only (counted, logged on disconnect). Subscription lifetime = flow lifetime (`try`/`finally`).
- `HttpApi[R >: OxStreams & WebSockets]`: REST endpoints stay `Any`, so stub tests keep working (`HttpApi[Any]`); SSE is tested against a real server on a random port.

## History and playback

### D68. Scope (2026-10-28)
- Every deduplicated live strike is stored and kept 7 days, and the window is refilled on start. The map gets a Playback mode; Live stays the default. The stats panel stays live during playback.
- **Rejected:** hourly aggregate tables (no reader yet; playback reads raw rows); history stats in the stats panel; a custom date-time picker (presets plus slider reach any moment, the API takes any range); storing raw `sig[]` (~40× the row).

### D69. SQLite on a Fly volume (2026-10-28)
- One file, `history.db`, via `sqlite-jdbc`, Magnum and Flyway (`flyway-core` has SQLite built in). WAL, `synchronous=NORMAL` and `busy_timeout` 5 s on every connection: writes from the batch writer and retention, reads from the API.
- `SQLiteDataSource` without a pool, since opening a connection is a file open. A Fly volume has the durability of a self-run Postgres (single host, daily snapshots) for ~$0.15–0.45/month, no Docker for dev, and tests use a real migrated temp file.
- **Rejected:** Postgres in any form (planned at first, D10): self-run on a second Fly app (~$5/month), Fly Managed (~$38–41/month), Neon/Supabase free tiers (compute hours, 500 MB, no backups). Also LiteFS/Litestream (not needed for now), HikariCP.

### D70. Strike table (2026-10-28)
- One `strikes` table: the `Strike` fields without `place`, `time_us INTEGER` (epoch µs), `delay_seconds REAL`, an index on `time_us`, no primary key. Places are located again on refill. Times are truncated to µs when parsed, so dedupe, recording, window, storage and JSON all carry the same instant and the UI doesn't show a restored strike twice.
- **Rejected:** a primary key (no reader); `UNIQUE(time, lat, lon)` (copies differ, D22); stored place names (stale when shapes change); nanoseconds (timing is µs-grade); ISO text (bigger, slower to compare).

### D71. Storing never blocks ingest (2026-10-28)
- Ingest `offer`s to `StrikeWriter`: an `ArrayBlockingQueue` of 10 000, full means drop and count. A fork takes a strike, drains up to 499 more, writes them in one transaction and sleeps 1 s. A failed batch is logged, counted and followed by 5 s of rest, not retried. No flush on shutdown (≤ 1 s lost). `stored` and `store dropped` go in the minute log.
- **Rejected:** backpressure into ingest (a slow disk would freeze the map and the socket); Ox `groupedWithin` (hidden buffers); retrying batches (state, double inserts); a transaction per strike.

### D72. History is optional (2026-10-28)
- `HISTORY_ENABLED` (default true), `HISTORY_FILE` (default `data/history.db`). If the file can't be opened or migrated, the app logs an ERROR and runs live without history.
- Writes, refill and retention run only with the live source. With `INGEST_SOURCE=replay` the file is opened, migrated and only read by `/history/*`, because replay times are fake (D20).
- Restored strikes from the last minute seed the deduplicator.
- **Rejected:** refusing to start without history (an outage for a nice-to-have); no database in replay (no offline playback development).

### D73. Retention (2026-10-28)
- A fork deletes rows older than `history.keep` (7 days) at start and hourly, with the cutoff from the app `Clock`. Errors are logged and the loop goes on. SQLite reuses the freed pages.
- **Rejected:** a daily delete (bigger bursts, up to 8 days kept); `VACUUM` (rewrites the file; the size is stable anyway).

### D74. History API (2026-10-30)
- Tag `history`, behind basic auth. `GET /history/range` → `{enabled, oldest?, newest?, keepDays}`. `GET /history/strikes?from&to` → `{from, to, step, strikes: [{timeMs, lat, lon}]}`, half-open `[from, to)`, oldest first. 400 for `to <= from`, a span over `history.keep` or a bad instant; 503 when history is off.
- At most `history.max-strikes` (100 000) rows: count first, then every `step`-th row by `row_number()`, which keeps the relative density.
- **Rejected:** full `StrikeResponse` rows (~2× larger); paging or streaming chunks; a 24 h max span (the owner wants 7 days); server-side gridding (a second rendering path).

### D75, D79. Playback in the map (2026-10-30)
- A mode inside `live-map`: the map instance, layers and camera stay. A plain Zustand store (`playback.store.ts`, no persist, so the map always opens live). The bar below the map shows only when `/history/range` says `enabled`: `Live | Playback`, play/pause, slider, presets 1 h / 6 h / 24 h / 7 d ending now (clamped to the oldest stored strike), speeds 1 min/s, 10 min/s, 1 h/s with 15 min, 1 h, 3 h trails. Picking Playback starts 6 h at 10 min/s, playing.
- `StrikeMap` takes `frames` (`snapshot`, `subscribe`, optional `onArrival`). Live redraws every 500 ms; playback redraws when the playhead moves. One clock: a 250 ms ticker advances the store by real time × speed, the gap clamped to 1 s.
- `StrikeTimeline` slices the sorted response by binary search and caps a frame at 10 000 features by every k-th strike, aligned to absolute indices so the same strikes stay picked.
- Flashes: while playing, forward only (not after a seek or a jump longer than one tick), `StrikeTimeline.arrivals` gives the strikes passed in `(previous, current]`, at most 4 per frame (≤ 16/s), spread evenly. The cap of 100 on screen and the reduced-motion rule are shared with live.
- SSE, the header badge and the stats stay live (a `Live` badge on the stats card). The live buffer is pruned every minute. Responses are dropped 30 s after leaving a range (`gcTime`), and `/history/range` is refetched after a minute. Slider step: 1 min up to 6 h, 5 min for 24 h, 30 min for 7 d; the shadcn slider is edited to pass `aria-label`/`aria-valuetext`.
- **Rejected:** a separate `playback` feature (would import `live-map`); reusing `StrikeBuffer` with a fake clock (it evicts); controls over the map (collide with attribution, cover storms on phones); a second redraw timer; starting paused under reduced motion; per-mode heatmap weights (playback looks denser; accepted); a flash per passed strike (hundreds per frame); flashing only the newest strikes (bunches at one edge); a separate flash style; flashing by duplicate count (duplicates mostly reflect regional overlap, not intensity; owner left flashes as they are).

## Frontend foundations

### D9, D31, D32. Scaffold (2026-10-18)
- Vite + React + TypeScript + Tailwind v4, pnpm. shadcn/ui from `shadcn init -t vite -b radix -p nova`: Radix base (Base UI is the default since 2026-07, but most examples assume Radix), Lucide icons, Geist font, CSS variables, dark via `class="dark"`.
- Oxlint, not ESLint (slower, more config).

### D33. Dev networking (2026-10-18)
- Vite on :5173 proxies `/api` to :8080, so the browser never makes cross-origin calls, SSE included.

### D41, D42. Structure and state (2026-10-23)
- `src/{app, api, features/<feature>, shared}`, containers vs components, dependency rule `api ← shared ← features ← app`, features never import each other. Enforced by Oxlint. Rulebook: `docs/frontend-conventions.md`.
- Server state: react-query. Client state: one Zustand store per concern. High-frequency data stays in plain objects outside React.
- **Rejected:** hooks + Context only (more boilerplate, re-render care).

### D43. Generated API client (2026-10-23)
- Orval generates `src/api` (react-query hooks + fetch, kebab-case, no index files, plain bodies) from `/api/v1/docs/docs.yaml`; committed; `pnpm api:generate` with the backend running. The SSE endpoint is tagged `stream` and excluded.
- **Rejected:** hand-written mirror types (D34).

### D45, D46, D47, D38. Formatting, linting, versions, tests (2026-10-23)
- Prettier: single quotes, semicolons, width 160, LF, sorted imports and Tailwind classes; `.editorconfig` matches.
- Oxlint: kebab-case files, no barrels, no import cycles, no default exports in `src/`, `type` over `interface`, `import type`, hook dependency checks, layer boundaries, no `../`. Stylelint for CSS. Feature CSS sits with its code; `styles/globals.css` holds only Tailwind and theme tokens.
- Every npm dependency is pinned exact (`saveExact`). Only `esbuild` may run install scripts.
- Vitest for pure logic first; component tests if a behaviour is worth pinning.

## Map and panel

### D8, D35, D36. Map stack (2026-10-18)
- `maplibre-gl` + `react-map-gl/maplibre` (exact versions), OpenFreeMap `dark`: WebGL heatmap and circle layers, no API key.
- MapLibre v6 resolves its worker next to its own file, which breaks under Vite. So the worker is imported with `?worker&url` and `setWorkerUrl` is called inside a lazy `import('maplibre-gl')` passed as `mapLib`, keeping MapLibre in its own chunk.
- **Rejected:** Leaflet (too slow at volume), OpenLayers (weak React story), CARTO basemaps (now need a key), mapcn (reference only), `optimizeDeps.exclude` (fixes dev only).

### D37, D39. Live strikes and animation (2026-10-18)
- `StrikeBuffer` lives outside React state (15-min window, deduped by time+lat+lon); MapLibre gets a snapshot via `setData` every 500 ms. `/strikes/recent` is reloaded on every stream (re)connect, with a manual retry every 5 s when `EventSource` gives up on an error response.
- Fade-out is data-driven: opacity and heatmap weight reach 0 at the end of the window. The arrival flash (~1.5 s) is a CSS animation on a short-lived `Marker`, for strikes from the live stream only (and playback, D79), at most 100 at once, hidden under `prefers-reduced-motion`.
- **Rejected:** animating a GeoJSON source per frame (`setData` ~30×/s starved basemap tiles).

### D40, D51. Layout and connection status (2026-10-23)
- The header badge (`online` / `offline`) is the only connection indicator, driven by the SSE stream. `/health` is for ops, not the UI. 12 s without a strike or heartbeat counts as a dead stream and reconnects (Vite's proxy can keep the browser's connection open after the backend dies).
- The stats panel is always shown. Below `lg` map and stats stack and the page scrolls (map `60dvh`, min 20rem); from `lg` the page fits the viewport and only the stats content scrolls. Picking a hotspot scrolls back to the map. The map attribution starts collapsed behind MapLibre's (i) button.
- **Rejected:** a foldable stats rail; a bottom sheet on phones; a strike-count pill on the map.

### D48, D49. Stats panel and fly-to (2026-10-23)
- `useGetStats` every 5 s. On error the last good data stays with a header note; no data + error shows "Stats unavailable". Per-minute chart with shadcn `chart` (Recharts), animation off so refreshes don't re-grow bars.
- `App` holds `MapFocus | null` and wires the stats `onHotspotSelect` to `LiveMap`, so features stay independent. `requestId` increments so repeated picks fly again. `flyTo` 1.6 s at zoom 6, `jumpTo` for reduced motion, deferred until the map loads.

## Packaging and deployment

### D59, D60. Image and static UI (2026-10-28)
- A separate `docker` sbt project (as in Bootzooka) depends on `backend`, runs `pnpm install --frozen-lockfile` + `pnpm build` as a resource generator and puts `ui/dist` into its jar under `webapp/`. `sbt docker/Docker/publishLocal` → `ride-the-lightning:latest` on `eclipse-temurin:25-jre`, with JVM flags from `jvmFlags` in `build.sbt` (same as `.jvmopts`, which is for sbt itself). Not aggregated by `root`, so `compile` and `testFull` never run pnpm.
- The backend serves it with tapir-files after the API, same origin, no CORS. No `index.html` fallback: there are no client routes, and a fallback would answer unknown `/api/v1/*` with 200 HTML. Unknown paths get an empty 404.
- **Rejected:** a hand-written multi-stage Dockerfile; copying the UI into `backend` resources (pnpm on every compile); a fat jar; SPA fallback; nginx in front.

### D61, D76. Container runtime and data volume (2026-10-28)
- The image sets `HTTP_HOST=0.0.0.0`, `GEO_CACHE_DIR=/opt/docker/data/geo` and `HISTORY_FILE=/opt/docker/data/history.db`. Fly mounts a 1 GB volume `data` at `/opt/docker/data` (daily snapshots kept 7 days, created once by hand), which pins the machine to the volume's host. Compose uses a named volume `data`, plus `./data` read-only at `/recordings`; live by default, `INGEST_SOURCE=replay` opts in.
- Fly's docs don't say who owns a mount, so the image has no final `USER`: `bin/docker-entrypoint.sh` starts as root, `chown`s the mount point to 1001 and `exec`s the start script through `setpriv`. The JVM runs as uid 1001 and stays the main process, so SIGTERM reaches it. The dir is also created owned by 1001 in the image, for new compose volumes.
- **Rejected:** bind-mounting `data/geo` (host uid ≠ 1001); shapes in the image; replay as the compose default; relying on Fly's undocumented chown; a one-off `chown` over ssh; running the JVM as root; `gosu`/`su-exec` (extra package); `su`/`runuser` (keep a root parent); a recursive `chown` on every start.

### D63. Basic auth in the backend (2026-10-28)
- Optional HTTP basic auth (`BASIC_AUTH_USER` / `BASIC_AUTH_PASSWORD`) on every endpoint, Swagger and the UI files, as a Tapir security input; a wrong password decodes as missing, so Tapir answers 401 with the challenge. `/api/v1/health` stays open for platform checks. Constant-time comparison. `REQUIRE_BASIC_AUTH=true` refuses to start without credentials. Off by default for local dev.
- **Rejected:** a Caddy/nginx sidecar; Cloudflare Access (needs a domain there); `fly proxy` only (no URL for a phone); a password check in the UI (the API stays open).

### D64. Hosting on Fly.io (2026-10-28)
- Private, owner only, behind basic auth, so the Blitzortung feed isn't republished. One `shared-cpu-1x` 1 GB machine in `ams`, never stopped (`auto_stop_machines = "off"`, `--ha=false`): ingest must keep running, and a second machine would open a second Blitzortung connection. Heap = 60% of the memory. flyctl pinned in `.mise.toml`.
- **Rejected:** free tiers that sleep (Render, Koyeb); free VMs (GCP e2-micro, Oracle: more admin, Oracle reclaims idle instances); a VPS (OS and Docker upkeep); a public deployment (Blitzortung terms).

### D62, D65, D66, D67. CI and deploy (2026-10-29)
- GitHub Actions on `ubuntu-24.04`, tools from `.mise.toml` via `jdx/mise-action`, with caches for coursier/sbt and `data/geo`. Jobs: `Backend tests` and `UI checks` in parallel, then `Docker image` (on `main` it pushes `registry.fly.io/ride-the-lightning:<sha>`), then `Deploy to Fly.io` on `main` (`fly deploy --image`, no build). So the image is built once and exactly that image ships. Runs are serialized per branch. Pull requests run the checks only.
- Auth is an app-scoped Fly deploy token in the `FLY_API_TOKEN` repo secret. Without Docker, a manual deploy uses Fly's remote builder (`--remote-only`).
- Test tables in the run summary: Vitest's built-in one, and sbt's JUnit XML through `mikepenz/action-junit-report` (`include_passed`, `group_suite`, `annotate_only`, so no check run or extra permissions). Actions run on Node 24.
- **Rejected:** `setup-java` + `setup-sbt` + `setup-node` (duplicates `.mise.toml`); one job with named steps (one box on the run page); a deploy job that rebuilds; ghcr.io (Fly would need registry credentials); an org-wide Fly token; `dorny/test-reporter` (needs `checks: write`); a hand-written XML-to-Markdown script.

### D80. Versioning (2026-10-30)
- SemVer `MAJOR.MINOR.PATCH`. `MAJOR.MINOR` is set by hand in `build.sbt` (starting at `1.0`: all planned phases are done and it runs in production). `PATCH` is the GitHub Actions run number, passed as `BUILD_NUMBER`, so it rises by itself on every build and a re-run keeps its number. PR runs use up numbers too, so deployed versions can skip some. `BUILD_COMMIT` carries the commit sha. Local builds are `1.0.0-dev` / `local`.
- `/api/v1/version` returns `version` and `commit`, and the footer's right corner shows `v1.0.6 · abc1234`, replacing the badge tooltip. Version → run → commit is one lookup, and the version is also the image's and jars' version; the Fly registry tag stays the sha.
- **Rejected:** commit-back bumps (a bot commit per build, CI loops, branch-protection friction); a git tag per build (needs `contents: write`, noise); `+sha` SemVer build metadata in the version (`+` isn't allowed in Docker tags); sbt-dynver (versions derived from tags, more machinery than a run number); date versions (no ordering within a day, and SemVer is the convention).

## Removed or replaced

### D10. Postgres (2026-10-15)
- Planned as a late phase, replaced by SQLite before it was built (D69).

### D34. Hand-written API types (2026-10-18)
- Replaced by Orval (D43).

### D53, D54, D55, D56, D58, D78. Timing-spread filter and station statistics (2026-10-30)
- Built in Phase 8 (`docs/phase-8.md` has the reasoning and the `mds` research), removed on the owner's call: the header switch, `/stats?maxMds` with its echo and `filteredOut`, the client-side filter and its store, the stations-used and timing-spread statistics, and `stations`/`mds` in API responses.
- `Strike.stations` and `Strike.mds` stay in the domain model and the `strikes` table: the decoder fills them and they cost little, so a later feature can use them without a migration.
- **Rejected at removal:** dropping the columns (a migration for no gain); keeping the filter but hiding the switch (dead code).
