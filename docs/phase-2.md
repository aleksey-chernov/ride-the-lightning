# Phase 2: Blitzortung ingest

## Run it
```bash
sbt "backend/runMain rtl.ingest.Record 600"   # record 10 min of live data to data/sample.jsonl (gitignored)
INGEST_SOURCE=replay sbt ~backend/reStart      # develop against the recording (preferred)
sbt ~backend/reStart                           # live feed (one websocket connection)
sbt testFull                                   # all tests (plain `test` only reruns affected ones in sbt 2)
```
Each strike is logged: `⚡  25.795  -80.359  region=0 stations=28 delay=4.0s`.
In IntelliJ: **Backend (replay)** run config (shared) sets `INGEST_SOURCE=replay`.

## Files
```
strikes/Strike.scala                      domain model, source-independent
ingest/StrikeSource.scala                 trait: def strikes: Flow[Strike]
ingest/IngestConfig.scala                 config + custom ConfigReader for the SourceKind enum
ingest/blitzortung/Lzw.scala              frame decoder (pure function)
ingest/blitzortung/BlitzortungMessage.scala  raw JSON model + toStrike
ingest/blitzortung/BlitzortungSource.scala   websocket client, retry, server rotation
ingest/ReplaySource.scala                 plays a recording in a loop, times shifted to now
ingest/Record.scala                       second main: records live messages to a file
test/resources/blitzortung/frames.json    3 real frames (raw + reference-decoded)
```

## How data flows
```
wss://wsN.blitzortung.org ──text frame──> Lzw.decode ──JSON──> BlitzortungMessage.parse ──> toStrike ──> Flow[Strike]
                                                                                                         │
Main: fork { strikes.runForeach(log) } <──────────────────────────────────────────────────────────────────┘
```

## Scala / library concepts
- **`trait` as an interface**: `StrikeSource` has two implementations; `Dependencies` picks one from config. Code downstream only sees `StrikeSource`.
- **`enum`**: `SourceKind` is a closed set of values (`Blitzortung`, `Replay`). `match` on it is checked: forget a case and the compiler warns.
- **`given`**: provides an instance the compiler passes implicitly. `given ConfigReader[SourceKind]` teaches pureconfig to read our enum (`ConfigReader[String].emap` = read a string, then convert or fail).
- **Default parameters** (`pol: Int = 0`): fields missing in JSON fall back to defaults; required ones (`time`, `lat`, `lon`) make parsing fail. Unknown fields are skipped, so new Blitzortung fields don't break us.
- **`Either[Throwable, A]`**: a result that is either an error (`Left`) or a value (`Right`). `Try(...).toEither` turns exceptions into values.
- **`var` and mutable collections**: fine when local and small (`Lzw`, a counter). Shared state stays immutable.
- **Ox `Flow`**: a lazy stream description, like `IAsyncEnumerable` that hasn't been enumerated. `Flow.usingEmit { emit => ... }` creates one from imperative code; `.mapConcat`, `.take`, `.runForeach`, `.runToList` transform and run it.
- **Ox `fork`**: starts a virtual thread in the current scope. In `Main` the strike consumer runs next to the HTTP server; if it throws, the scope (the app) stops.
- **Ox `retry`**: `retry(RetryConfig(schedule, afterAttempt = ...))(op)` reruns `op` when it throws. Schedule: exponential from 1 s, capped at 1 min, with jitter. No `maxRetries` = forever.
- **Ox resources**: `useCloseableInScope(DefaultSyncBackend())` closes the HTTP client when the scope ends (≈ `await using` / DI-disposed singleton).
- **sttp websockets (sync)**: `basicRequest.get(uri"wss://...").response(asWebSocket { ws => ... }).send(backend)`; inside, `ws.sendText` / `ws.receiveText()` block on a virtual thread. A closed socket throws `WebSocketClosed`, which triggers the retry.

## Protocol notes
- Connect to a random server, send `{"a":111}`, then each text frame is one LZW-compressed JSON strike.
- LZW: code < 256 is a literal char; ≥ 256 indexes a dictionary built while decoding (each step adds previous phrase + first char of current). A code not yet in the dictionary means `prev + prev.head`.
- `time` is epoch **nanoseconds**; parsed as `Long`, it's more precise than JavaScript's float.
- The same strike can arrive more than once with different `region` values (overlapping regions). Deduplication happens in Phase 3.

## Try
1. Set a breakpoint in `Lzw.decode`, run `LzwTest` in Debug, watch `dict` grow.
2. Log only strikes with `stations >= 20`: add `.filter(_.stations >= 20)` before `.runForeach` in `Main`.
3. `REPLAY_SPEED=10 INGEST_SOURCE=replay sbt backend/run`: a 10× fast-forward.
4. Pull the network cable (or toggle Wi-Fi) while live: watch `Connection #n lost` and the growing backoff.
