# Phase 8: richer stats

## Run it
```bash
INGEST_SOURCE=replay sbt ~backend/reStart   # or REPLAY_SPEED=5 for busier stats
cd ui && pnpm dev                           # http://localhost:5173
```
The stats card gains a **Detection, last 10 min** section: p50/p90/p99 of stations used per strike, publication delay and timing spread (`mds`). Hotspots are now 0.5° cells, labelled with 2 decimals (`41.75°N 2.25°E`).

Flip the switch in the header, **Timing spread ≤ 10 µs** (below `sm`, 640 px, the switch shows without its label). The map thins out at once, and as soon as the new stats arrive the card reads `Refreshed every 5 s · timing spread ≤ 10 µs · N hidden (last hour)`. Every window-based number (counts, chart, places, hotspots, detection) now counts only the strikes that passed; the received/duplicates totals are ingest-wide. Reload the page and the switch is still on. Turn it off and the hidden strikes come back without a refetch.

Try it at 320, 390 and 1440 px wide: the header must not scroll sideways.

API by hand: `curl 'localhost:8080/api/v1/stats?maxMds=10000'`. `maxMds=0` returns 400.

Checks: `sbt testFull`, and in `ui/`: `pnpm test`, `pnpm lint`, `pnpm format:check`, `pnpm build`.

## Files
```
backend/src/main/scala/rtl/ingest/blitzortung/BlitzortungMessage.scala  decodes sig[].status; stations = entries with status & 4
backend/src/main/scala/rtl/strikes/Strike.scala       scaladoc: stations used, a lower bound when sig is full
backend/src/main/scala/rtl/strikes/StrikesApi.scala   @description on StrikeResponse.stations
backend/src/main/scala/rtl/stats/StatsResponse.scala  Percentiles, Detection, maxMds, filteredOut
backend/src/main/scala/rtl/stats/Stats.scala          maxMds filter, 0.5° hotspots, nearest-rank percentiles
backend/src/main/scala/rtl/stats/StatsApi.scala       optional ?maxMds query param, validated 1..1,000,000
backend/src/test/scala/rtl/stats/StatsApiTest.scala   stub server: echo, filteredOut, 400 on maxMds=0 (/stats test moved from StrikesApiTest)
backend/src/test/scala/rtl/stats/StatsTest.scala      filter, 0.5° hotspots, detection percentiles
backend/src/test/scala/rtl/ingest/blitzortung/BlitzortungMessageTest.scala  counts only stations with status & 4
backend/src/test/scala/rtl/TestStrikes.scala          stations, delaySeconds, mds parameters
ui/src/shared/stores/strike-filter.store.ts   Zustand persist: tightOnly, TIGHT_MDS_NS, selectMaxMds
ui/src/shared/utils/units.utils.ts (+ .test.ts)  formatMicros, formatSeconds
ui/src/shared/components/ui/switch.tsx        shadcn switch (generated)
ui/src/app/layout/strike-filter-toggle.tsx    switch + label (label from sm, aria-label always)
ui/src/app/layout/app-header.tsx              reads the store, renders the toggle
ui/src/features/live-map/lib/strike-buffer.ts snapshot(nowMs, maxMds = null) skips strikes that don't pass
ui/src/features/live-map/components/strike-map.tsx  setData right away when maxMds changes; flash skips hidden strikes, one flash counter
ui/src/features/stats/containers/stats-panel.tsx    useGetStats({ maxMds }) with keepPreviousData
ui/src/features/stats/components/detection-section.tsx  the three percentile rows + filter note
ui/src/features/stats/components/stat-tile.tsx      tile shared by the headline and detection
ui/src/features/stats/components/stats-headline.tsx uses StatTile
ui/src/features/stats/components/stats-panel-card.tsx  dims the body while isPlaceholderData; DetectionSection
ui/src/features/stats/components/hotspot-list.tsx   0.5° cells
ui/src/features/stats/utils/stats-format.utils.ts (+ .test.ts)  describeStats from the maxMds echo; 2-decimal coordinates
ui/src/features/live-map/containers/live-map.tsx    reads selectMaxMds, passes it to StrikeMap
ui/src/api/                                         regenerated (GetStatsParams, Detection, Percentiles)
```

## How data flows
```
header switch ──> useStrikeFilterStore (persisted in localStorage as rtl.strikeFilter)
                        │ selectMaxMds: tightOnly ? TIGHT_MDS_NS : null
          ┌─────────────┴─────────────┐
          ▼                           ▼
   StatsPanel                      LiveMap ──> StrikeMap
   useGetStats({ maxMds })            StrikeBuffer.snapshot(now, maxMds) ──> setData
   GET /stats?maxMds=10000            (buffer keeps every strike; only the snapshot filters)
          │
   StrikeWindow ──> Stats.compute(..., maxMds): filter, then counts / places / hotspots / detection
```
The stream (`/strikes/stream`) and `/strikes/recent` are **not** filtered (D55). Every `StrikeResponse` already carries `mds`, so the map can filter on its own. A filtered stream would need a reconnect on every toggle and would make the buffer forget strikes it might need again a second later. Nothing changes upstream either: one Blitzortung connection, the same traffic.

## Concepts
- **What `sig` and `status` mean**: each strike lists the stations that heard it in `sig`. Each station's `status` is a set of bit flags, and Blitzortung's [compendium](https://www.blitzortung.org/en/compendium.php) documents three of them: 1 = negative polarity, 2 = positive polarity, 4 = "signal is used for the computation". The sample also has bits 8 and 16, which are undocumented, so we ignore them. Until now `stations` was `sig.size`. That count is wrong twice over: the list includes stations the solver didn't use, and the feed cuts it at 40 entries (about 58% of the sample strikes have exactly 40). Now `stations` counts entries with `status & 4` (D53). In the sample that gives roughly 8–32 per strike, median ~21. The minimum is exactly 8, which matches the "at least 8 stations" rule reported for the network. **It's a lower bound when the list is full**: in most capped strikes the last of the 40 entries is itself used, so more used stations were probably cut off. One forum post shows the scale: a strike with "detectors involved: 177, detectors used: 12" ([forum](https://forum.blitzortung.org/mybb/showthread.php?tid=3728)). In C# terms, `(status & 4) != 0` is `status.HasFlag(Used)` on a `[Flags] enum`.
- **What `mds` is, and what it isn't**: Blitzortung defines it as the "maximal deviation span in nano seconds" ([forum, 2015](https://forum.blitzortung.org/mybb/showthread.php?tid=1450); [compendium](https://www.blitzortung.org/en/compendium.php)). It measures how far the used stations' arrival times disagree with the computed position, so lower means they agree better. It is **not** an accuracy radius: a network operator wrote "I wouldn't nail accuracy down to the mds" ([forum, 2016](https://forum.blitzortung.org/mybb/printthread.php?tid=1634)). Radio covers about 300 m per µs, so 10 µs is "roughly 3 km of timing disagreement", but that conversion is ours and not official. Some projects on GitHub read `mds` as a station count or an intensity; none of them cite a source.
- **Why the cut-off is 10 µs, and why it's ours** (D54): Blitzortung publishes no quality threshold. In the recorded sample (200 strikes over about 2 minutes, so treat every number as approximate) `mds` runs from about 2 to 15 µs, with a median near 8.4 µs, and nothing above 15 µs. That suggests the server already drops worse solutions, so a 15 µs filter would hide nothing. 7.5 µs keeps only ~44% of strikes; 10 µs keeps about two thirds. It's one constant, `TIGHT_MDS_NS`, and every label is built from it with `formatMicros`, so changing it changes the UI too. The detection section shows live `mds` percentiles so you can check the choice against real data. A missing `mds` decodes as 0, and 0 doesn't pass (`0 < mds ≤ max`), on both server and client. For the same reason the detection percentiles skip `mds` and `delay` values of 0, so a missing field can't drag them down.
- **`delay`**: seconds from the strike to its publication on the feed, so it's lag, not quality. That's why the section is called "Detection", not "Quality". The sample has two groups: most strikes arrive after ~3–6 s, and about 10% after ~20–36 s. A median alone (~4 s) hides the slow group, so p90 (~23–25 s) and p99 are shown too.
- **Nearest-rank percentiles**: sort the values; pN is the value at position `ceil(N/100 × n)` (1-based). For the ten values 1..10: p50 is the 5th (5), p90 the 9th (9), p99 is `ceil(9.9)` = the 10th (10). The result is always a value that actually occurred, there's no interpolation, and it fits in one line of Scala (`Stats.percentiles`). Sorting ten minutes of strikes every 5 s is cheap at our scale (D25). SQL Server's `PERCENTILE_DISC` works the same way; `PERCENTILE_CONT` is the interpolating kind.
- **Filter after dedupe**: the same strike often arrives two or three times from different regional solvers, and the copies can disagree a lot (`mds` 14553 vs 6760 for one pair in the sample). The `Deduplicator` keeps the first copy, so that copy's `mds` is what the filter sees. The filter therefore has to run on the deduplicated window, which is where `Stats.compute` already reads from.
- **Grid cells with `floor`**: `cell(x) = floor(x / 0.5) * 0.5` gives a cell's south-west corner, and the centre is `+0.25`. `floor` matters for negative coordinates: for −86.6, `floor(−173.2) = −174`, so the corner is −87.0 and the centre −86.75. Truncation (`toInt`) rounds toward zero instead and would give −86.5, shifting every cell west of Greenwich or south of the equator by one. Centres end in .25 or .75, which are binary fractions (1/4, 3/4) and therefore exact in a `Double`, so equal cells compare equal and the labels show no `0.7499999`. Ties are sorted by `(-count, lat, lon)`, so the list order doesn't depend on `HashMap` iteration order and the tests can assert it.
- **Optional query parameters in Tapir**: `query[Option[Int]]("maxMds").validateOption(Validator.inRange(1, 1_000_000))`. A missing parameter is `None`, a present one is validated, and a bad value gets a 400 before the handler runs. It's the ASP.NET `[FromQuery, Range(1, 1000000)] int? maxMds` with automatic model validation. The parameter appears in the OpenAPI spec, so Orval generates `GetStatsParams` with `maxMds?: number`.
- **`Option` in responses**: `maxMds` and the three `Detection` fields are `Option`s, which jsoniter omits from the JSON when empty, so `detection` serialises as `{}` when the last 10 minutes are empty. Tapir marks them optional, so the generated TypeScript types say `delaySeconds?: Percentiles` and the UI has to handle "absent". Collections are still always present (D44).
- **react-query keys and `keepPreviousData`**: the generated hook puts its params into the query key (`['/api/v1/stats', { maxMds: 10000 }]`), so a new `maxMds` is a different cache entry and is fetched on its own. No manual `refetch` is needed. A new key normally starts empty and would flash skeletons. `placeholderData: keepPreviousData` shows the previous key's data until the new response arrives, and `isPlaceholderData` is true meanwhile, so the card dims its body for that moment. The description is built from the response's `maxMds` echo, never from the switch, so it always describes the numbers on screen.
- **Zustand `persist`**: wrapping the store creator in `persist(..., { name: 'rtl.strikeFilter' })` saves the state to `localStorage` and restores it on load, like a user setting saved in the browser. The store holds a boolean (`tightOnly`), not the threshold, so changing `TIGHT_MDS_NS` later applies to everyone who already turned the filter on. It lives in `shared/stores/` because the header, the map and the stats panel all read it, and features may not import each other (D58).
- **A filter that doesn't drop data**: `StrikeBuffer` keeps every strike and applies `maxMds` only when it builds a snapshot. Strikes that don't pass are still evicted by age. `StrikeMap` lists `maxMds` in its effect dependencies and pushes one `setData` immediately when it changes, so the map responds at once instead of on the next 500 ms tick.

## Try
1. Set `TIGHT_MDS_NS = 7_500` in `shared/stores/strike-filter.store.ts`, turn the switch on and compare the hidden count. The labels follow the constant.
2. Compare the list size with the stations actually used in the sample:
   `jq -c '[(.sig|length), ([.sig[] | select((.status // 0) % 8 >= 4)] | length), .mds]' data/sample.jsonl | head -20`
3. Set `CellDegrees = 1.0` in `Stats.scala`. The Catalonia cluster merges back into one hotspot, and the tests fail until you update them.
4. `curl 'localhost:8080/api/v1/stats?maxMds=3000'` and watch most strikes disappear into `filteredOut`.
