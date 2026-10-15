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

## Stack
- Backend (`backend/`): Scala 3, sbt 2, Tapir netty-sync, Ox (direct style), sttp client4, jsoniter-scala, pureconfig, logback, ScalaTest. Manual DI in `Dependencies.scala`.
- Frontend (`ui/`, from Phase 5): Vite, React, TypeScript, Tailwind v4, shadcn/ui (Radix base), MapLibre via react-map-gl, OpenFreeMap dark style, react-query, EventSource (SSE).
- Tooling: mise (`.mise.toml`: Temurin 25, sbt, Node 24, pnpm), IntelliJ IDEA Ultimate + Scala plugin.

## Commands
- Install tools: `mise install`
- Backend dev: `sbt ~backend/reStart` (from Phase 1); tests: `sbt test`
- UI dev: `cd ui && pnpm dev` (from Phase 5)
