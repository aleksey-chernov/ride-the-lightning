# Plan

Each phase ends runnable and gets its own commit. Tick when done.

## Architecture (in-memory)
```
StrikeSource (Blitzortung | Replay) --Flow[Strike]--> Ingest: Deduplicator --> StrikeWindow (last 60 min)
   StrikeWindow --> GET /api/v1/strikes/recent
   StrikeWindow --> Stats.compute (per-minute, per-region, hotspots) --> GET /api/v1/stats
   Ingest --> StrikeBroadcaster --> GET /api/v1/strikes/stream (SSE, one bounded queue per client)
```

## Phases
- [x] **0. Repo, harness, tooling, IDE.** Git, CLAUDE.md, decisions log, mise, scalafmt, IntelliJ setup (`docs/phase-0.md`).
- [x] **1. Backend skeleton.** sbt build, `Main extends OxApp`, Tapir `/api/v1/version` + `/health`, Swagger at `/api/v1/docs`, pureconfig, logback, custom `reStart`.
- [x] **2. Blitzortung ingest.** `Strike` model, `StrikeSource`, `Lzw.decode` + tests, lenient jsoniter codec, `BlitzortungSource` (sttp ws, `{"a":111}`, retry/backoff/server rotation), `record` mode → `sample.jsonl`, `ReplaySource`. App logs strikes.
- [x] **3. In-memory processing + REST.** `Deduplicator`, `StrikeWindow`, pure `Stats.compute`, `/strikes/recent`, `/stats`, stub-server tests.
- [x] **4. SSE streaming.** `StrikeBroadcaster` fed by `Ingest`, `/strikes/stream` (strike + heartbeat events), bounded per-client queues, drop-on-slow-consumer, real-server SSE test.
- [x] **5. Frontend skeleton.** `ui/` Vite + React + TS + Tailwind v4 + shadcn (Radix, Nova), Oxlint, dark shell, Vite proxy `/api` → `:8080`, backend status via react-query, attribution footer.
- [x] **6. Live map.** MapLibre + OpenFreeMap dark, `/strikes/recent` + EventSource → `StrikeBuffer` → `setData` every 500 ms, heatmap + fading dots, live status, Vitest.
- [ ] **7. Stats panel.** shadcn cards, Recharts per-minute rate, hotspots table with fly-to. Optional OpenAPI → TS codegen.
- [ ] **8. Richer stats.** Per-region names, stations-per-strike, delay percentiles, `mds` quality filter, 0.5° hotspots.
- [ ] **9. Packaging.** sbt-native-packager single image (backend serves UI), docker-compose, GitHub Actions CI.
- [ ] **10. Persistence.** Postgres (compose), Flyway, Magnum, batched inserts, hourly aggregates, history endpoints, embedded-PG tests.

Stretch: `GlmSource` (NOAA), OpenTelemetry + `grafana/otel-lgtm`, H3 hexbins, storm-cell tracking, MTG LI, globe projection.

## Blitzortung protocol notes
- Servers `wss://ws1|ws2|ws7|ws8.blitzortung.org` (443); random pick, rotate on failure.
- After open send `{"a":111}`; each text frame = one LZW-compressed JSON strike.
- LZW: code < 256 literal; ≥ 256 dictionary; missing code → `prev + prev.head`; add `prev + cur.head` each step.
- Fields: `time` (epoch ns, Long), `lat`, `lon`, `alt`, `pol`, `mds`, `mcg`, `status`, `region`, `delay`, `latc`/`lonc`, `sig[]` (`sta`, `time`, `lat`, `lon`, `alt`, `status`). Parse leniently.
- Refs: github.com/akeamc/blitzortung (`src/live.rs`), github.com/Satyasuranjeet/live_lightning_data.
