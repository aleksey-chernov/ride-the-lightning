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
