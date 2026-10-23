# Phase 7: stats panel

## Run it
```bash
INGEST_SOURCE=replay sbt ~backend/reStart   # or REPLAY_SPEED=5 for busier stats
cd ui && pnpm dev                           # http://localhost:5173
```
The stats card shows, top to bottom: counts for the last minute, 10 minutes and hour, plus ingest totals and the duplicate rate; a bar chart of strikes per minute over the last hour; the busiest 1° cells (hotspots) of the last 10 minutes, each named after its most common place; the top places (countries and seas, with a wave icon for water). Click a hotspot and the map flies there. Stop the backend: the card keeps the last data and says `Backend unreachable, showing last data`. On a phone (below Tailwind's `lg`, 1024 px) map and stats stack and the page scrolls; picking a hotspot scrolls back up to the map.

Checks: `pnpm test`, `pnpm lint`, `pnpm format:check`, `pnpm build`.

## Files
```
backend/src/main/scala/rtl/geo/NaturalEarth.scala  downloads Natural Earth 1:50m GeoJSON (pinned commit) into data/geo once, parses it
backend/src/main/scala/rtl/geo/Place.scala      Place(name, kind: Country | Sea), Place.OpenOcean
backend/src/main/scala/rtl/geo/PlaceIndex.scala indexes the shapes (242 countries + 118 seas), locate(lat, lon) -> Place
backend/src/main/scala/rtl/ingest/Ingest.scala  sets strike.place once per new strike
backend/src/main/scala/rtl/stats/Stats.scala    top places (last 10 min) and Hotspot.place
ui/src/features/stats/
  containers/stats-panel.tsx                 polls useGetStats every 5 s, renders the card
  components/stats-panel-card.tsx            card layout + loading / error / data states
  components/stats-headline.tsx              count tiles and ingest totals
  components/strikes-per-minute-chart.tsx    Recharts bar chart via the shadcn chart wrapper
  components/hotspot-list.tsx                hotspot buttons -> onHotspotSelect
  components/place-list.tsx                  top places, wave icon for seas, proportional bar
  utils/stats-format.utils.ts (+ .test.ts)   pure formatting and mapping
ui/src/shared/components/ui/chart.tsx        shadcn chart (generated)
ui/src/shared/types/map-focus.ts             MapFocus: the contract between app and the map
ui/src/features/live-map/components/strike-map.tsx   flyTo / jumpTo on each focus request
ui/src/app/app.tsx                           holds focus state, wires stats -> map
```

## How data flows
```
GET /api/v1/stats (every 5 s, react-query) ──> StatsPanel ──> StatsPanelCard ──> tiles, chart, lists
hotspot click ──> onHotspotSelect ──> App: setFocus({ lon, lat, zoom: 6, requestId + 1 })
                                          └──> LiveMap ──> StrikeMap effect ──> map.flyTo(...)
```

## Concepts
- **Naming places (PlaceIndex)**: Blitzortung's `region` ids overlap, so the backend names each strike from its coordinates instead (D50). Each polygon's bounding box is registered in every 1° grid cell it touches; a lookup only tests the polygons of the strike's cell. The test is ray casting: cast a ray from the point and count how often it crosses the ring's edges; odd means inside. A point inside an outer ring but also inside one of its holes (a lake, an enclave) is outside. Countries are tried before seas, so a coastal strike gets its country. Natural Earth's sea polygons leave gaps in open water, so a miss falls back to the nearest shape edge within 3° (longitude scaled by `cos(lat)` so degrees are comparable), else "Open ocean". The data is Natural Earth (public domain), credited in the footer anyway.
- **Polling with react-query**: `refetchInterval: 5000` refetches on a timer. While a refetch is in flight, or after one fails, `data` still holds the last good response, so the card never blanks; `isError` only changes the note in the header. The first load has no data yet, so skeletons show.
- **Containers and components**: `StatsPanel` is the only place that touches the hook and the store. Everything in `components/` takes props and callbacks, so it can be read (and later tested) without a backend.
- **shadcn chart and Recharts**: shadcn's `chart.tsx` is a thin wrapper around Recharts. `ChartContainer` takes a config (`count: { label, color: 'var(--chart-1)' }`) and exposes each colour as a CSS variable (`--color-count`), so bars follow the theme tokens. Animation is off: with a refresh every 5 s, animated bars would keep re-growing.
- **Features talk through app**: `stats` and `live-map` never import each other. `App` holds `focus: MapFocus | null`, gets it from the stats callback and hands it to the map. `requestId` increases on every click, so picking the same hotspot twice flies again (a same-value object would otherwise look like "no change" to someone comparing coordinates).
- **Imperative map calls in an effect**: the camera move is a one-off command, not state to render, so `StrikeMap` calls `map.flyTo` inside `useEffect([focus, loaded])`. If the click arrives before the map has loaded, the effect waits for `loaded`. `prefers-reduced-motion` gets `jumpTo` instead of a flight.
- **Pure helpers with tests**: coordinate formatting (`41.5°N 2.5°E`, S/W, 0 with no letter), the duplicate rate (0 when nothing arrived) and chart point mapping (UTC minute → local `HH:mm`) live in `utils/` and are covered by Vitest.

## Try
1. Change the poll interval in `containers/stats-panel.tsx` and watch the Network tab.
2. Turn `isAnimationActive` on for the `<Bar>` and see why it's off.
3. Click the same hotspot twice after panning away: it flies back each time.
