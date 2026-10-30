# Plan

Each phase ends runnable and gets its own commit. Tick when done.

## Architecture
```
StrikeSource (Blitzortung | Replay) --Flow[Strike]--> Ingest: Deduplicator --> PlaceIndex (Natural Earth) --> StrikeWindow (last 60 min)
   Ingest --> StrikeWriter (bounded queue, batches) --> history.db (SQLite, 7 days; live source only) <-- Retention (hourly)
   history.db --> refill on start --> StrikeWindow + Deduplicator
   history.db --> GET /api/v1/history/range, /history/strikes?from&to (thinned) --> UI Playback mode
   StrikeWindow --> GET /api/v1/strikes/recent
   StrikeWindow --> Stats.compute (per-minute, per-place, 0.5° hotspots, delay percentiles) --> GET /api/v1/stats
   Ingest --> StrikeBroadcaster --> GET /api/v1/strikes/stream (SSE, one bounded queue per client)
   classpath webapp/ (built UI, docker image only) --> GET /
```

## Phases
- [x] **0. Repo, harness, tooling, IDE.** Git, CLAUDE.md, decisions log, mise, scalafmt, IntelliJ setup (`docs/phase-0.md`).
- [x] **1. Backend skeleton.** sbt build, `Main extends OxApp`, Tapir `/api/v1/version` + `/health`, Swagger at `/api/v1/docs`, pureconfig, logback, custom `reStart`.
- [x] **2. Blitzortung ingest.** `Strike` model, `StrikeSource`, `Lzw.decode` + tests, lenient jsoniter codec, `BlitzortungSource` (sttp ws, `{"a":111}`, retry/backoff/server rotation), `record` mode → `sample.jsonl`, `ReplaySource`. App logs strikes.
- [x] **3. In-memory processing + REST.** `Deduplicator`, `StrikeWindow`, pure `Stats.compute`, `/strikes/recent`, `/stats`, stub-server tests.
- [x] **4. SSE streaming.** `StrikeBroadcaster` fed by `Ingest`, `/strikes/stream` (strike + heartbeat events), bounded per-client queues, drop-on-slow-consumer, real-server SSE test.
- [x] **5. Frontend skeleton.** `ui/` Vite + React + TS + Tailwind v4 + shadcn (Radix, Nova), Oxlint, dark shell, Vite proxy `/api` → `:8080`, backend status via react-query, attribution footer.
- [x] **6. Live map.** MapLibre + OpenFreeMap dark, `/strikes/recent` + EventSource → `StrikeBuffer` → `setData` every 500 ms, heatmap + fading dots, live status, Vitest.
- [x] **6a. Frontend structure and tooling.** `app/api/features/shared` layout, containers vs components, Zustand, Orval-generated API, response contract, Prettier/Stylelint/Oxlint rules, exact versions (`docs/frontend-conventions.md`).
- [x] **7. Stats panel.** shadcn cards, Recharts per-minute rate, hotspots table with fly-to, place names (countries/seas). Uses the generated `useGetStats` hook.
- [x] **8. Richer stats.** Stations used per strike, detection percentiles (stations, delay, mds), `mds` timing-spread filter, 0.5° hotspots. (Filter, stations and `mds` stats removed later, D78.)
- [x] **9. Packaging.** sbt-native-packager single image (`docker` project bundles the built UI, backend serves it at `/`), docker-compose with a shapes volume, GitHub Actions CI (tests, UI checks, image build), private Fly.io deployment behind basic auth.
- [x] **10. Persistence and playback.** SQLite file on a Fly volume (WAL), Flyway, Magnum, a non-blocking batch writer (drop when full), 7-day retention, window refill on restart, `/history/range` + `/history/strikes` (thinned), map Playback mode (presets, slider, speeds), entrypoint that chowns the data volume.

Stretch: `GlmSource` (NOAA), OpenTelemetry + `grafana/otel-lgtm`, H3 hexbins, storm-cell tracking, MTG LI, globe projection.

## Blitzortung protocol notes
- Servers `wss://ws1|ws2|ws7|ws8.blitzortung.org` (443); random pick, rotate on failure.
- After open send `{"a":111}`; each text frame = one LZW-compressed JSON strike.
- LZW: code < 256 literal; ≥ 256 dictionary; missing code → `prev + prev.head`; add `prev + cur.head` each step.
- Fields: `time` (epoch ns, Long), `lat`, `lon`, `alt`, `pol`, `mds`, `mcg`, `status`, `region`, `delay`, `latc`/`lonc`, `sig[]` (`sta`, `time`, `lat`, `lon`, `alt`, `status`). Parse leniently.
- `sig[].status` bit flags (compendium): 1 = negative polarity, 2 = positive polarity, 4 = signal used for the computation; 8 and 16 also occur, undocumented. `sig` is capped at 40 entries, so stations used (`status & 4`) is a lower bound when it's full.
- `mds` = maximal deviation span in ns (timing disagreement of the used stations, lower is better; not an accuracy radius, no official threshold). `mcg` = maximal circular gap in degrees. `delay` = seconds from strike to publication.
- Refs: github.com/akeamc/blitzortung (`src/live.rs`), github.com/Satyasuranjeet/live_lightning_data.
