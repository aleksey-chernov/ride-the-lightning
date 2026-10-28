# Ride the Lightning

Educational, local-only app: Scala 3 backend ingests the Blitzortung live lightning feed, aggregates stats in memory, streams to a React + shadcn/ui map. The owner is **new to Scala**, so work moves in small, runnable phases.

## Before doing anything
1. Read `docs/decisions.md`. Don't re-propose rejected options unless new facts appear.
2. Read `docs/plan.md` for the current phase and next step.

## Rules
- **Log decisions:** any decision reached in conversation is appended to `docs/decisions.md` in the same turn (date, decision, why, rejected).
- **Comments:** short one-liners, strictly to the point, only where code isn't self-explanatory. Concept explanations go in `docs/phase-N.md`.
- One phase at a time: implement → user runs it → tick `docs/plan.md` → commit.
- **Commits:** single line, starts with a capital letter, no type/area prefix (e.g. `Add health endpoint`). One sentence: no trailing period; several sentences: normal punctuation.
- Blitzortung etiquette: one websocket connection, backoff + jitter on reconnect, no republishing, attribution in UI, develop against the replay sample.
- Simplicity over Bootzooka parity; check patterns at github.com/softwaremill/bootzooka when needed.
- **Frontend:** follow `docs/frontend-conventions.md` (structure, layers, naming, imports, state, styling, tests). Exact npm versions only.

## Stack
- Backend (`backend/`): Scala 3, sbt 2, Tapir netty-sync, Ox (direct style), sttp client4, jsoniter-scala, pureconfig, logback, ScalaTest. Manual DI in `Dependencies.scala`.
- Frontend (`ui/`, from Phase 5): Vite, React, TypeScript, Tailwind v4, shadcn/ui (Radix base), MapLibre via react-map-gl, OpenFreeMap dark style, react-query (Orval-generated hooks), Zustand, EventSource (SSE); Prettier, Oxlint, Stylelint.
- Tooling: mise (`.mise.toml`: Temurin 25, sbt, Node 24, pnpm), IntelliJ IDEA Ultimate + Scala plugin.

## Commands
- Install tools: `mise install`
- Backend dev: `sbt ~backend/reStart` (live) or `INGEST_SOURCE=replay sbt ~backend/reStart` (recording)
- Record live data: `sbt "backend/runMain rtl.ingest.Record 600"` → `data/sample.jsonl`
- Place shapes: downloaded from Natural Earth (pinned commit) into `data/geo/` on the first backend start or test run; delete the folder to re-download
- Tests: `sbt testFull` (plain `test` only reruns affected tests in sbt 2)
- UI dev: `cd ui && pnpm dev` (http://localhost:5173, proxies `/api` to :8080)
- UI checks: `pnpm lint` (Oxlint + Stylelint), `pnpm format:check`, `pnpm test`, `pnpm build`; fix with `pnpm lint:fix`, `pnpm format`
- Regenerate API client after backend API changes: backend running, then `cd ui && pnpm api:generate`; commit `ui/src/api`
- Docker image: `sbt docker/Docker/publishLocal` (builds the UI too) → `ride-the-lightning:latest`; run with `docker compose up` (http://localhost:8080, `INGEST_SOURCE=replay docker compose up` for the recording). `sbt docker/Universal/stage` gives the same app without Docker: `target/out/jvm/scala-3.9.0/docker/universal/stage/bin/ride-the-lightning`
- Deploy to Fly.io (private, basic auth from `fly secrets`): `sbt docker/Docker/stage`, then `fly deploy target/out/jvm/scala-3.9.0/docker/docker/stage --config "$PWD/fly.toml" --ha=false --remote-only`; logs: `fly logs -a ride-the-lightning`; change the login: `fly secrets set BASIC_AUTH_USER=... BASIC_AUTH_PASSWORD=...` (restarts the app). CI deploys every push to `main`
- Add shadcn components: `cd ui && pnpm dlx shadcn@latest add <name>` (lands in `src/shared/components/ui`)
