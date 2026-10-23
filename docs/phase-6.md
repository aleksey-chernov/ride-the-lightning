# Phase 6: live map

## Run it
```bash
INGEST_SOURCE=replay sbt ~backend/reStart   # or REPLAY_SPEED=5 for a busier map
cd ui && pnpm dev                           # http://localhost:5173
```
The map shows the last 15 minutes: a heatmap glow when zoomed out, one dot per strike when zoomed in. Each strike arriving over the stream flashes (expanding ring + bright core, ~1.5 s); dots then fade from white through yellow and orange to violet and dissolve completely before they're removed at 15 minutes. The header shows the single connection status (`live · backend <version>` / `backend offline · reconnecting…`), driven by the stream; the map shows the strike count. Stop the backend and the status switches to offline; start it again and the map refills. The stats panel folds into a slim rail (button in its corner); the choice is remembered in `localStorage` (Zustand `persist`).

Checks: `pnpm test` (Vitest), `pnpm lint`, `pnpm build`.

## Files
Paths as of the frontend restructure (see `docs/frontend-conventions.md`):
```
ui/src/features/live-map/
  containers/live-map.tsx        connects the feed, renders map + count
  components/strike-map.tsx      react-map-gl <Map>, GeoJSON source, 500 ms setData loop
  components/strike-count.tsx    strike count badge on the map
  hooks/use-live-strikes.ts      owns buffer + connection, publishes status/count to the store
  stores/live-feed.store.ts      Zustand: connection status + count (read by the app header)
  lib/strike-buffer.ts           recent strikes outside React state; dedupe, eviction, GeoJSON snapshot
  lib/strike-buffer.test.ts      Vitest tests for the buffer
  lib/strike-stream.ts           EventSource on /strikes/stream + /strikes/recent on every (re)connect
  lib/strike-flash.ts/.css       arrival flash: a short-lived MapLibre Marker animated by CSS
  lib/maplibre.ts                lazy MapLibre import + worker URL fix
  constants/map-layers.ts        heatmap and circle layer styles (MapLibre expressions)
  constants/map-style.ts         basemap URL, initial view, attribution
ui/src/features/stats/           foldable stats panel (store + rail/card components); content arrives in Phase 7
ui/src/shared/components/connection-badge.tsx   the single connection indicator (header)
```

## How data flows
```
/strikes/recent (on connect) ─┐
                              ├─> StrikeBuffer.add (dedupe by time+lat+lon)
/strikes/stream  (SSE) ───────┘          │
                                         │  every 500 ms
                        snapshot(now): evict > 15 min, age 0..1 ──> source.setData ──> MapLibre (WebGL)
                                         │  every 1 s
                                    setCount(size) ──> React re-renders the badge only
```

## Concepts
- **Why not React state for strikes**: putting every strike in `useState` would re-render the component tree several times a second. The buffer is a plain object held for the component's lifetime (`useState(() => new StrikeBuffer(...))`), and MapLibre is updated directly through `mapRef.getMap().getSource('strikes').setData(...)`. React only re-renders for things that change rarely: status, and a count once a second.
- **MapLibre GL**: draws the map with WebGL on the GPU. Data goes into a **source** (GeoJSON here), and **layers** decide how it looks. Styles are data-driven **expressions**: `['interpolate', ['linear'], ['get', 'age'], 0, '#ffffff', 1, '#7c3aed']` = "colour by the feature's `age`, white at 0, violet at 1". `['zoom']` makes size and opacity depend on zoom level.
- **GeoJSON order**: coordinates are `[lon, lat]`, the opposite of how people say them. There's a test for that.
- **EventSource**: the browser's SSE client. It reconnects by itself after a dropped connection, but gives up on an error response (e.g. the dev proxy's 502 when the backend is down), so `strikeStream.ts` retries every 5 s in that case. Every successful (re)connect reloads `/strikes/recent` to fill any gap; duplicates are ignored by the buffer.
- **Effect cleanup**: `useEffect(() => followStrikes(...), [...])` returns the disconnect function, so React closes the stream when the component unmounts (and in dev StrictMode, which mounts twice on purpose to catch missing cleanups).
- **MapLibre's worker**: tile and GeoJSON processing runs in a Web Worker that v6 looks for next to its own file. Vite relocates that file (dev pre-bundling, prod hashing), so `maplibre.ts` imports the worker with `?worker&url` (Vite bundles it and gives back a URL) and calls `setWorkerUrl` before the first map. It's done inside a lazy `import()` passed as `mapLib`, so MapLibre (~290 kB gzipped) stays in its own chunk.
- **Two animation speeds**: the slow fade (15 min) is data-driven: `age` feeds opacity, colour and heatmap weight, refreshed every 500 ms, ending at opacity 0 so eviction is invisible. The fast arrival flash (1.5 s) is a CSS animation on an HTML marker, not map data: pushing GeoJSON 30×/s into MapLibre flooded its worker and starved the basemap tiles. CSS animations run on the browser's compositor, and each marker removes itself after the animation. `StrikeBuffer.onArrival` notifies only for new strikes from the live stream, so the initial `/strikes/recent` load doesn't light up the whole map. `prefers-reduced-motion` hides the flash.
- **`MapView` name**: react-map-gl's default export is called `Map`; importing it as `MapView` avoids shadowing JavaScript's built-in `Map`.

## Try
1. Zoom into a storm cell until the heatmap fades and individual dots appear.
2. In `layers.ts`, change the `circle-color` stops and watch Vite hot-reload the map.
3. Change the window: `WINDOW_MINUTES = 5` in `features/live-map/containers/live-map.tsx` and compare the count.
4. Browser dev tools → Network → `stream`: watch the `strike` events arrive.
