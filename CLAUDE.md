# Ride the Lightning

Educational app, deployed privately behind a login: Scala 3 backend ingests the Blitzortung live lightning feed, aggregates stats in memory, keeps 7 days of history in SQLite, streams to a React + shadcn/ui map. The owner is **new to Scala**, so work moves in small, runnable phases.

## Before doing anything
1. Read `docs/decisions.md`. Don't re-propose rejected options unless new facts appear.
2. Read `docs/plan.md` for the current phase and next step.

## Rules
- **Log decisions:** any decision reached in conversation goes into `docs/decisions.md` in the same turn, under its topic (date, decision, why, rejected). If it changes an older decision, rewrite that entry instead of adding a contradiction.
- **Comments:** short one-liners, strictly to the point, only where code isn't self-explanatory. Concept explanations go in `docs/phase-N.md`.
- One phase at a time: implement → user runs it → tick `docs/plan.md` → commit.
- **Commits:** single line, starts with a capital letter, no type/area prefix (e.g. `Add health endpoint`). One sentence: no trailing period; several sentences: normal punctuation.
- Blitzortung etiquette: one websocket connection, backoff + jitter on reconnect, no republishing, attribution in UI, develop against the replay sample.
- Simplicity over Bootzooka parity; check patterns at github.com/softwaremill/bootzooka when needed.
- **Frontend:** follow `docs/frontend-conventions.md` (structure, layers, naming, imports, state, styling, tests). Exact npm versions only.

## Stack
- Backend (`backend/`): Scala 3, sbt 2, Tapir netty-sync, Ox (direct style), sttp client4, jsoniter-scala, pureconfig, logback, SQLite (sqlite-jdbc, WAL) + Magnum + Flyway, ScalaTest. Manual DI in `Dependencies.scala`.
- Frontend (`ui/`, from Phase 5): Vite, React, TypeScript, Tailwind v4, shadcn/ui (Radix base), MapLibre via react-map-gl, OpenFreeMap dark style, react-query (Orval-generated hooks), Zustand, EventSource (SSE); Prettier, Oxlint, Stylelint.
- Tooling: mise (`.mise.toml`: Temurin 25, sbt, Node 24, pnpm), IntelliJ IDEA Ultimate + Scala plugin.

## Commands
- Install tools: `mise install`
- Backend dev: `sbt ~backend/reStart` (live) or `INGEST_SOURCE=replay sbt ~backend/reStart` (recording)
- Record live data: `sbt "backend/runMain rtl.ingest.Record 600"` → `data/sample.jsonl`
- Place shapes: downloaded from Natural Earth (pinned commit) into `data/geo/` on the first backend start or test run; delete the folder to re-download
- Strike history: SQLite file `data/history.db` (`HISTORY_FILE`; image: `/opt/docker/data/history.db` on the `data` volume), written only with the live source; replay reads it (`INGEST_SOURCE=replay HISTORY_FILE=<copy> sbt ~backend/reStart` to develop playback offline); `HISTORY_ENABLED=false` turns it off; schema changes go in a new `backend/src/main/resources/db/migration/V<n>__*.sql`
- Version: `MAJOR.MINOR` by hand in `build.sbt`, `PATCH` = CI run number (`BUILD_NUMBER`), `BUILD_COMMIT` = sha; local builds are `1.0.0-dev`; `GET /api/v1/version` and the footer show it
- Tests: `sbt testFull` (plain `test` only reruns affected tests in sbt 2)
- UI dev: `cd ui && pnpm dev` (http://localhost:5173, proxies `/api` to :8080)
- UI checks: `pnpm lint` (Oxlint + Stylelint), `pnpm format:check`, `pnpm test`, `pnpm build`; fix with `pnpm lint:fix`, `pnpm format`
- Regenerate API client after backend API changes: backend running (e.g. `INGEST_SOURCE=replay`), then `cd ui && pnpm api:generate`; commit `ui/src/api`
- Docker image: `sbt docker/Docker/publishLocal` (builds the UI too) → `ride-the-lightning:latest`; run with `docker compose up` (http://localhost:8080, `INGEST_SOURCE=replay docker compose up` for the recording). `sbt docker/Universal/stage` gives the same app without Docker: `target/out/jvm/scala-3.9.0/docker/universal/stage/bin/ride-the-lightning`
- Deploy to Fly.io (private, basic auth from `fly secrets`): `sbt docker/Docker/stage`, then `fly deploy target/out/jvm/scala-3.9.0/docker/docker/stage --config "$PWD/fly.toml" --ha=false --remote-only`; logs: `fly logs -a ride-the-lightning`; change the login: `fly secrets set BASIC_AUTH_USER=... BASIC_AUTH_PASSWORD=...` (restarts the app). CI deploys every push to `main`
- Fly data volume (shapes + `history.db` at `/opt/docker/data`): the owner creates it once, before the first deploy with `[[mounts]]`: `fly volumes create data -a ride-the-lightning --region ams --size 1 --snapshot-retention 7 --yes`; check with `fly volumes list -a ride-the-lightning`; no `sqlite3` in the image, copy the file out with `fly ssh sftp get /opt/docker/data/history.db <local path> -a ride-the-lightning` (also `history.db-wal` next to it; it's a live file, so recopy if the backend logs `malformed`)
- Add shadcn components: `cd ui && pnpm dlx shadcn@latest add <name>` (lands in `src/shared/components/ui`)
