# Phase 0: repo, tooling, IDE

## What was added
| File | Purpose |
|---|---|
| `.mise.toml` | Pins tool versions for this folder: Temurin JDK 25 (LTS), sbt 2.0.10, Node 24, pnpm. `cd` into the repo and mise activates them. |
| `.scalafmt.conf` | Scala formatter config (Scala 3 dialect, new syntax, optional braces). Both IntelliJ and sbt use it. |
| `.editorconfig` | Basic whitespace rules every editor respects. |
| `.jvmopts` | JVM flags for sbt itself (terminal and IntelliJ sbt shell): allow `sun.misc.Unsafe` and native access, which JDK 24+ otherwise warns about when Scala 3 / Netty use them. |
| `.gitignore` / `.gitattributes` | Ignore build output (`target/`, `node_modules/`), force LF line endings. |
| `CLAUDE.md` | Project brief + rules Claude reads at the start of every session. |
| `docs/decisions.md` | Append-only log of decisions, so we don't revisit settled questions. |
| `docs/plan.md` | Phase checklist + protocol notes. |
| `.claude/settings.json` | Pre-approves common dev commands to cut permission prompts. |

## Key concepts
- **mise**: a per-project version manager (like nvm/sdkman for everything). `mise install` downloads what `.mise.toml` lists; `java -version` inside the repo then shows 25.
- **sbt**: Scala's build tool. It reads `build.sbt` (Scala code describing the build), downloads dependencies from Maven Central, compiles, tests, runs. It keeps a long-running JVM for speed, so start it once with `sbt` and type commands at its prompt.
- **Why JDK 25, not the installed 27**: 25 is LTS; libraries and IDE tooling target LTS first.

## IntelliJ IDEA setup (do once, after Phase 1 adds `build.sbt`)
1. Settings → Plugins → install **Scala** (JetBrains). Restart.
2. File → Project Structure → SDKs → add JDK from `~/.local/share/mise/installs/java/temurin-25…`.
3. File → Open → select the repo folder → "Open as sbt project".
4. Settings → Build Tools → sbt: tick **use sbt shell for builds and imports**. Required: sbt 2 links `target/out` class files to read-only files in its cache (`~/.cache/sbt/v2/cas`), so IntelliJ's own compiler fails with `AccessDeniedException` when it writes there.
5. Tests: shared run configurations **Backend tests** (IntelliJ's ScalaTest runner, results tree) and **Backend tests (sbt)** (`testFull` in the sbt shell). For gutter ▶ runs set Run → Edit Configurations → Edit configuration templates → ScalaTest → working directory `$PROJECT_DIR$` (tests read `data/geo` relative to the repo root).
6. Settings → Editor → Code Style → Scala → Formatter: **Scalafmt**; enable "Reformat on save" under Tools → Actions on Save.
7. Later (Phase 5): Node interpreter from mise and pnpm as package manager under Languages & Frameworks → Node.js.
