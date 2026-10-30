# Ride the Lightning

[![CI](https://github.com/aleksey-chernov/ride-the-lightning/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/aleksey-chernov/ride-the-lightning/actions/workflows/ci.yml)

A live world map of lightning strikes. A Scala 3 backend reads the Blitzortung feed, removes duplicate strikes, keeps statistics in memory and 7 days of history in SQLite. A React app draws the strikes as they arrive, shows statistics and hotspots, and plays back stored time ranges.

It's an educational project, built in small phases and deployed privately behind a login, so the feed isn't republished.

## Run it

Tools come from [mise](https://mise.jdx.dev): `mise install` (Temurin 25, sbt, Node, pnpm).

```bash
INGEST_SOURCE=replay sbt ~backend/reStart   # backend on :8080, playing a recording from data/sample.jsonl
cd ui && pnpm install && pnpm dev           # UI on http://localhost:5173
```

Recordings aren't committed. Make one with `sbt "backend/runMain rtl.ingest.Record 600"`, which connects to the live feed for 10 minutes.

Tests: `sbt testFull`, and `pnpm test` in `ui/`. Every push to `main` runs them, builds the image and deploys it to Fly.io.

## Docs

- [`CLAUDE.md`](CLAUDE.md): all commands and project rules
- [`docs/plan.md`](docs/plan.md): architecture and phases
- [`docs/decisions.md`](docs/decisions.md): what was decided, and why
- `docs/phase-N.md`: what each phase built and the concepts behind it
- [`docs/frontend-conventions.md`](docs/frontend-conventions.md): UI structure and rules

## Attribution

Lightning data © [Blitzortung.org](https://www.blitzortung.org) contributors (CC BY-SA 4.0). Places: [Natural Earth](https://www.naturalearthdata.com). Map: [OpenFreeMap](https://openfreemap.org).
