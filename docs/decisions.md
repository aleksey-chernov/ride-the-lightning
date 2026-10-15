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
