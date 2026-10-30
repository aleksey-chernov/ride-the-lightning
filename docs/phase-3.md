# Phase 3: in-memory processing + REST

## Run it
```bash
INGEST_SOURCE=replay sbt ~backend/reStart     # or IntelliJ "Backend (replay)"
curl -s "localhost:8080/api/v1/strikes/recent?minutes=5&limit=10"
curl -s localhost:8080/api/v1/stats
```
Every minute the log prints a summary: `Last minute: 29 strikes · total 38 received, 9 duplicates dropped`.
Swagger (`/api/v1/docs`) shows both endpoints, including the query parameter limits.

## Files
```
ingest/Ingest.scala            pipeline: source → Deduplicator → StrikeWindow; IngestCounters
strikes/Deduplicator.scala     drops repeats of the same strike
strikes/StrikeWindow.scala     last 60 min of strikes, thread-safe, lock-free
strikes/StrikesConfig.scala    window length from config
strikes/StrikesApi.scala       GET /strikes/recent?minutes=1..60&limit=1..50000; StrikeResponse, RecentStrikesResponse
stats/StatsResponse.scala      response types (StatsResponse, MinuteCount, PlaceCount, Hotspot)
stats/Stats.scala              pure compute function → StatsResponse
stats/StatsApi.scala           GET /stats
test: TestClock, TestStrikes, DeduplicatorTest, StrikeWindowTest, StatsTest, StrikesApiTest, ConfigTest
```

## How data flows
```
fork: source.strikes ──> Deduplicator.isNew? ──yes──> StrikeWindow.add        (one writer)
                                     └─no──> counters.duplicates++
HTTP: /strikes/recent ──> window.recent(minutes)                              (many readers)
      /stats          ──> Stats.compute(window.snapshot, counters, now)
```

## Why duplicates happen
Blitzortung computes strikes per region; where regions overlap, the same strike arrives once per region, either identical or re-solved slightly differently (microseconds and < 1 km apart). In a 200-message recording: 37 exact repeats plus 17 near-repeats. Real separate strokes are milliseconds apart, so the rule is: **same strike if < 1 ms and < 0.02° apart**, remembered for 1 minute.

## Scala / library concepts
- **Immutable data + `AtomicReference`**: the window is a `Vector[Strike]` that is never modified; `add` builds a new vector and swaps it in with `updateAndGet`. Readers call `get()` and get a consistent snapshot without locks. C# twin: `ImmutableList` + `Interlocked.CompareExchange` / `ImmutableInterlocked.Update`.
- **Pure functions**: `Stats.compute(strikes, received, duplicates, now)` reads nothing global (no clock, no state), so tests just call it with fixed inputs.
- **Injected `Clock`**: anything time-dependent takes a `java.time.Clock`; tests pass `TestClock` and move time by hand (≈ .NET 8 `TimeProvider` / `FakeTimeProvider`).
- **Collections API**: `groupMapReduce(key)(map)(reduce)` = group, map each, combine (≈ LINQ `GroupBy(...).Select(g => g.Count())` in one pass). `filterNot`, `sortBy`, `take`, `toList`.
- **Parameter untupling**: `.map((place, count) => PlaceCount(place.name, ..., count))` takes a tuple apart directly.
- **Default arguments**: `Deduplicator(clock, memory = Duration.ofMinutes(1))`; `Dependencies.create(config, clock = Clock.systemUTC())`.
- **Domain vs response types**: the domain `Strike` has no JSON concerns; `/strikes/recent` returns `RecentStrikesResponse(strikes: List[StrikeResponse])`, built with the factory `StrikeResponse.from(strike)` in the companion object (≈ a static `FromDomain` method or a mapping profile). Wrapping the list in an object leaves room for new fields without breaking clients. API types are named `XResponse` / `XRequest`.
- **Tapir query parameters**: `query[Int]("minutes").default(15).validate(Validator.inRange(1, 60))` = typed, defaulted, validated, and documented in Swagger; invalid input returns a JSON 400.
- **Durations in config**: HOCON-style `60 minutes` is read as Scala `FiniteDuration`, then converted with `.toJava`. `ConfigTest` loads the real `application.conf` so a bad value fails a test, not a startup.

## Try
1. `curl -s "localhost:8080/api/v1/strikes/recent?minutes=999"`: read the validation error.
2. Add `last5Minutes` to `StatsResponse` and `Stats.compute`, and see `StatsTest` + Swagger pick it up.
3. Lower `MaxDegreesDiff` to `0.001` and compare `duplicatesDropped` in `/stats` after a minute.
4. Debug: breakpoint in `Ingest.run` on the `else` branch to catch a duplicate live.
