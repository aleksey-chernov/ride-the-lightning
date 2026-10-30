# Phase 10: persistence and playback

Every deduplicated live strike is now stored in a SQLite file and kept 7 days. A restart refills the 60-minute window from it, and the map gets a Playback mode that replays a stored range as a time-lapse (D68-D76).

## Run it
Dev, live feed (stores strikes in `data/history.db` at the repo root; `data/` is git-ignored):
```bash
sbt ~backend/reStart          # log: Flyway "Successfully applied 1 migration", then "Restored N strikes from history"
cd ui && pnpm dev             # http://localhost:5173, the Live | Playback bar is under the map
```
The minute log now ends with `· stored X, store dropped Y`. Stop and start the backend: the map comes back with the last hour instead of an empty one.

Offline, against a stored file (replay mode only reads it, D72, because replay strike times are fake):
```bash
fly ssh sftp get /opt/docker/data/history.db /tmp/history.db -a ride-the-lightning           # a copy of the Fly data
fly ssh sftp get /opt/docker/data/history.db-wal /tmp/history.db-wal -a ride-the-lightning   # its recent commits
INGEST_SOURCE=replay HISTORY_FILE=/tmp/history.db sbt ~backend/reStart
```
The files are copied while the app writes them. If a checkpoint (see WAL below) rewrites `history.db` during the copy, the copy is torn: the backend logs `malformed` or "History is off". Copy both files again then.

Turn it off: `HISTORY_ENABLED=false`. If the file can't be opened (unwritable folder, corrupt file), the app logs an ERROR and runs live without history; the Playback bar then doesn't show.

Docker: `docker compose up` now mounts one `data` volume at `/opt/docker/data` (shapes + `history.db`). The old `geo` volume isn't used any more; `docker volume rm ride-the-lightning_geo` deletes it.

Fly, **once, before the first deploy of this phase** (CI deploys on push to `main`, and a deploy with `[[mounts]]` fails while no volume `data` exists):
```bash
fly volumes create data -a ride-the-lightning --region ams --size 1 --snapshot-retention 7 --yes
fly volumes list -a ride-the-lightning          # after the deploy: ATTACHED VM is the machine id
fly volumes snapshots list <volume id>          # daily snapshots, kept 7 days
```
The deploy then replaces the machine with one on the volume's host. The image has no `sqlite3`; to look inside, copy the file with `fly ssh sftp get` as above and open it locally.

## Files
```
build.sbt                                       magnum, sqlite-jdbc, flyway-core; image: HISTORY_FILE, data dir, entrypoint
backend/src/main/resources/application.conf     history { enabled, file, keep = 7 days, max-strikes }
backend/src/main/resources/db/migration/V1__create_strikes.sql   the strikes table + time index
backend/src/main/scala/rtl/db/Db.scala          SQLiteDataSource with WAL/synchronous/busy_timeout, Flyway migrate, transact
backend/src/main/scala/rtl/db/DbCodecs.scala    Instant <-> epoch µs
backend/src/main/scala/rtl/history/
  HistoryConfig.scala                           the config section
  StrikeRepository.scala                        insert, since, page (thinning), range, deleteOlderThan
  StrikeWriter.scala                            bounded queue + batch writer fork; drops when full
  Retention.scala                               deletes rows older than 7 days, at start and hourly
  HistoryApi.scala, HistoryResponse.scala       GET /api/v1/history/range and /history/strikes
backend/src/main/scala/rtl/Dependencies.scala   openHistory: on / off / failed, read-only in replay
backend/src/main/scala/rtl/Main.scala           refill, writer + retention forks, minute log
backend/src/main/scala/rtl/ingest/Ingest.scala  offers each new strike to the writer; restore()
backend/src/main/scala/rtl/strikes/             StrikeWindow.addAll, Deduplicator.remember
backend/src/main/scala/rtl/ingest/blitzortung/BlitzortungMessage.scala  times truncated to µs
backend/src/test/scala/rtl/TestDb.scala         a migrated temp file per test
backend/src/test/scala/rtl/history/             repository + retention, writer, API tests
backend/src/test/scala/rtl/ingest/IngestTest.scala   restore relocates, refills, seeds dedupe
docker/src/universal/bin/docker-entrypoint.sh   chown the data mount, drop to uid 1001
docker-compose.yml, fly.toml                    the data volume
ui/src/api/endpoints/history/, ui/src/api/models/  generated history hooks and types
ui/src/features/live-map/
  constants/playback.ts                         presets, speeds, tick
  utils/playback.utils.ts                       rangeFor, formatPlayhead, preset availability
  stores/playback.store.ts                      mode, range, playhead, speed, playing
  lib/strike-timeline.ts                        binary-search slices of a loaded range, frame cap
  lib/strike-frames.ts                          what StrikeMap draws: snapshot + subscribe
  hooks/use-playback-ticker.ts                  the one playback clock
  hooks/use-playback-frames.ts, use-history-range.ts, use-history-strikes.ts
  containers/playback-controls.tsx              store + queries -> the bar
  components/playback-bar.tsx, playback-transport.tsx, playback-options.tsx
ui/src/shared/components/ui/{slider,toggle,toggle-group}.tsx   shadcn; slider edited for aria labels
```

## How it fits together
```
Ingest ──new strike──> StrikeWriter.offer ──queue (10 000, drop when full)──> fork: take + drainTo ──> INSERT batch ──> history.db
Retention fork ──hourly──> DELETE WHERE time_us < now - 7 days
start: history.db ──since(window cutoff)──> Ingest.restore ──> PlaceIndex ──> StrikeWindow.addAll + Deduplicator.remember
UI Playback: /history/range ──> presets ──> /history/strikes?from&to ──> StrikeTimeline ──ticker moves playhead──> StrikeMap
```

## Concepts
- **SQLite**: a whole SQL database in one file, opened by a library inside our process; there's no server to run. `sqlite-jdbc` bundles the native SQLite for each OS and exposes it through JDBC (Java's standard database API, like ADO.NET). Types are loose: `INTEGER`, `REAL` (8-byte double, so 5.2 stays 5.2), `TEXT`, `BLOB`. There's no timestamp type, so times are stored as epoch microseconds (`DbCodecs`).
- **WAL and the three pragmas** (set on every connection, `Db.open`): in WAL (write-ahead log) mode a commit appends to `history.db-wal`, and readers keep reading the main file meanwhile, so the API never waits for the writer. SQLite copies the WAL back now and then (a checkpoint). `synchronous=NORMAL` skips a disk flush per commit; with WAL a power cut can lose the last commits but never corrupts the file. `busy_timeout=5000` makes a connection wait up to 5 s for a lock instead of failing at once.
- **No connection pool** (D69): a pool keeps expensive network connections open for reuse. Opening a SQLite connection is a file open, so `SQLiteDataSource` opens one per transaction.
- **Flyway migrations**: SQL files in `db/migration` named `V1__create_strikes.sql`, `V2__...`. On start Flyway compares them with its `flyway_schema_history` table and runs the new ones, in order, once. Never edit an applied file (its checksum is checked); add `V2` instead. Like EF Core migrations, but plain SQL.
- **Magnum**: a thin layer over JDBC. `sql"... WHERE time_us >= $from"` looks like string interpolation but turns each `$x` into a `?` parameter (no SQL injection) and encodes the value with a `DbCodec`. `.query[Row].run()` reads rows into a case class (`derives DbCodec` maps columns by position), `.update.run()` returns the changed row count, `batchUpdate` sends many rows on one statement. `transact(...) { ... }` commits when the block returns and rolls back when it throws. The `DbTx ?=>` in its signature is a *context function*: inside the block a transaction is available implicitly, and `sql"...".run()` only compiles there.
- **Codecs via `given`**: `DbCodecs` defines `given DbCodec[Instant]`, built from the `Long` codec with `biMap(toInstant, toLong)`. `import rtl.db.DbCodecs.given` brings it into scope, so `derives DbCodec` on `StrikeRow` finds it for the `time` field.
- **The writer queue and why it drops** (D71): ingest must never wait for the disk, or a slow write would freeze the live map and the Blitzortung socket. `offer` puts the strike in a bounded `ArrayBlockingQueue` and returns at once; if the queue is full, the strike is counted as dropped. A fork blocks on `take()` for the first strike, `drainTo`s up to 499 more, writes them in one transaction, then sleeps 1 s so the next batch gathers about a second's worth. A failed batch is logged, counted and followed by 5 s of rest. The `sleep` sits outside `try`, because Ox stops a fork by interrupting it, and a `catch` around the sleep could swallow that.
- **Retention**: a fork deletes rows older than 7 days at start and every hour. The cutoff comes from the app `Clock`, never `now()` in SQL, so tests can move time. SQLite keeps the freed pages and reuses them, so the file stops growing after a week.
- **Refill on restart**: before the server and ingest start, `Main` loads the strikes since the window's cutoff. `Ingest.restore` locates their places again (places aren't stored; shapes can change), adds them to the window in one update (`StrikeWindow.addAll`) and remembers the last minute in the deduplicator, so late region copies of those strikes are still dropped.
- **µs truncation**: Blitzortung sends nanoseconds, the database keeps microseconds. If only the stored copy were truncated, a restored strike wouldn't equal its live original, and the UI buffer (which dedupes on the exact time string) would show it twice. So times are truncated once, when a message is parsed, and every copy agrees.
- **History API and thinning** (D74): `/history/range` says whether history is on and the oldest/newest stored time. `/history/strikes?from&to` returns `[from, to)`, oldest first, with only time and position. Above 100 000 rows it counts first, then keeps every `step`-th row using the SQL window function `row_number() OVER (ORDER BY time_us)`. Every n-th strike keeps the relative density: a storm stays denser than a quiet area. The UI shows "Showing 1 in n strikes".
- **Optional history** (D72): `Dependencies.openHistory` returns `None` when it's off or the file fails to open, and everything downstream takes an `Option`. With `INGEST_SOURCE=replay` the file is read but never written.
- **Playback frames** (D75): `StrikeMap` no longer knows where strikes come from. It gets `frames`: `snapshot()` (the strikes to draw now) and `subscribe(redraw)`. Live frames come from the `StrikeBuffer` every 500 ms as before; playback frames come from the `StrikeTimeline` whenever the playhead moves. The map stays mounted across modes, so the camera and layers stay.
- **Timeline and frame cap**: a range is loaded once, sorted by time. A frame is the slice `[playhead - trail, playhead]`, found by binary search, so scrubbing backwards is as cheap as forwards and nothing is evicted. Above 10 000 features a frame takes every k-th strike, counted from the start of the whole array, so the same strikes stay picked from frame to frame (no flicker).
- **One clock**: `usePlaybackTicker` runs every 250 ms and moves the store's playhead by real elapsed time × speed. The gap is clamped to 1 s, because browsers throttle timers in background tabs and a 5-minute gap would jump the playhead. Only `PlaybackControls` re-renders on each tick; the map redraws through its subscription, without React.
- **Memory while in playback**: the SSE stream keeps running so the header and stats stay live. `StrikeBuffer.prune` now runs every minute on its own, so the buffer doesn't grow while the map draws playback. A loaded range is dropped 30 s after you leave it (`gcTime`).
- **Heatmap brightness**: playback trails can be longer than the live 15 minutes of fading dots, so the heatmap looks denser in playback. Accepted on purpose (D75).
- **Edited shadcn component**: `slider.tsx` passes `thumbLabel`/`thumbValueText` to the thumb as `aria-label`/`aria-valuetext`. `pnpm dlx shadcn@latest add slider --overwrite` would drop that edit.
- **Fly volumes** (D76): a volume is a slice of an NVMe disk on one physical server. It attaches to one machine, so the machine now always runs on that server (if the server fails, the app is down until it's back or restored). Fly takes a daily block-level snapshot, kept 7 days here; restore with `fly volumes create data --snapshot-id <id> ...`. A snapshot can be up to a day old.
- **Who owns the mount** (D76): a volume is mounted over `/opt/docker/data` when the machine starts, hiding what the image had there, and Fly's docs don't promise an owner. The JVM runs as uid 1001 and needs to write there. So the image now starts as root: `docker-entrypoint.sh` runs `chown 1001:0 /opt/docker/data` and then `exec setpriv --reuid=1001 --regid=0 --clear-groups bin/ride-the-lightning`. `setpriv` (from util-linux, already in the Ubuntu base) changes the user and group of the current process and then runs the command in it; `exec` replaces the shell. So the JVM is still the container's main process (PID 1 under Docker), gets SIGTERM directly and never runs as root. Look at `target/out/jvm/scala-3.9.0/docker/docker/stage/Dockerfile`: it ends with `ENTRYPOINT ["/opt/docker/bin/docker-entrypoint.sh"]` and no `USER 1001:0`.

## Try
1. With the dev backend running for a few minutes: `sqlite3 data/history.db "SELECT count(*), datetime(min(time_us)/1e6, 'unixepoch') FROM strikes"` (if `sqlite3` is installed), or open the file in IntelliJ's Database tool window (SQLite driver).
2. `curl -s localhost:8080/api/v1/history/range`, then `curl -s "localhost:8080/api/v1/history/strikes?from=<oldest>&to=<newest>" | head -c 300`. Try `to` before `from` and get a 400.
3. Set `max-strikes = 100` in `application.conf`, restart, ask for a long range and look at `step`. Put it back.
4. `HISTORY_FILE=/proc/nope/history.db sbt ~backend/reStart`: an ERROR in the log, the live map works, and the Playback bar is gone.
5. In the UI pick Playback, 24 h, 1 h/s; drag the slider backwards while it plays, and watch strikes flash as the playhead passes them (at most 4 per frame).
6. `sbt docker/Docker/stage` and read the end of the Dockerfile; then read `docker/src/universal/bin/docker-entrypoint.sh`. After the deploy, `fly ssh console -a ride-the-lightning -C "ls -ln /opt/docker/data"` shows `geo` and `history.db` owned by 1001. With Docker, `docker compose exec ride-the-lightning grep Uid /proc/1/status` shows PID 1 (the JVM) running as 1001. On Fly, PID 1 is Fly's own init, which starts our entrypoint.
