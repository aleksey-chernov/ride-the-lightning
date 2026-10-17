# Plan

Each phase ends runnable and gets its own commit. Tick when done.

## Architecture (in-memory)
```
StrikeSource (Blitzortung | Replay) --Flow[Strike]--> Hub (Ox fan-out)
   ├─> RecentStrikes   (ring buffer, last 30 min)          -> GET /api/v1/strikes/recent
   ├─> StatsAggregator (per-minute, per-region, hotspots)   -> GET /api/v1/stats
   └─> SSE broadcaster (one subscriber per browser)         -> GET /api/v1/strikes/stream
```

## Phases
- [x] **0. Repo, harness, tooling, IDE.** Git, CLAUDE.md, decisions log, mise, scalafmt, IntelliJ setup (`docs/phase-0.md`).
- [x] **1. Backend skeleton.** sbt build, `Main extends OxApp`, Tapir `/api/v1/version` + `/health`, Swagger at `/api/v1/docs`, pureconfig, logback, custom `reStart`.
- [x] **2. Blitzortung ingest.** `Strike` model, `StrikeSource`, `Lzw.decode` + tests, lenient jsoniter codec, `BlitzortungSource` (sttp ws, `{"a":111}`, retry/backoff/server rotation), `record` mode → `sample.jsonl`, `ReplaySource`. App logs strikes.
- [ ] **3. In-memory processing + REST.** Dedupe (same strike per region), Hub fan-out, `RecentStrikes`, `StatsAggregator`, `/strikes/recent`, `/stats`, stub-server tests.
- [ ] **4. SSE streaming.** `/strikes/stream`, per-client subscriber, drop-on-slow-consumer, `curl -N` check.
- [ ] **5. Frontend skeleton.** `ui/` Vite + React + TS + Tailwind v4 + shadcn, dark shell, Vite proxy `/api` → `:8080`, version via react-query.
- [ ] **6. Live map.** MapLibre + OpenFreeMap dark, EventSource → ref buffer → throttled `setData`, heatmap + fading circles, attribution.
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
