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
