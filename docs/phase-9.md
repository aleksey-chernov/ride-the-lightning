# Phase 9: packaging

## Run it
With Docker running:
```bash
sbt docker/Docker/publishLocal          # pnpm install + build, then the image ride-the-lightning:latest
docker compose up                       # http://localhost:8080, live feed
INGEST_SOURCE=replay docker compose up  # plays data/sample.jsonl instead
```
One port serves everything: the map at `/`, the API at `/api/v1`, Swagger at `/api/v1/docs`. The first start downloads the Natural Earth shapes into the `geo` volume (watch the log); later starts reuse them. `docker compose down` keeps the volume; `docker compose down -v` deletes it.

Without Docker, the same app as the image runs from a staged folder:
```bash
sbt docker/Universal/stage
INGEST_SOURCE=replay target/out/jvm/scala-3.9.0/docker/universal/stage/bin/ride-the-lightning
```
Dev is unchanged: `sbt ~backend/reStart` + `pnpm dev` (Vite on :5173). The backend alone doesn't serve the UI there.

Checks: `sbt testFull`; `sbt docker/Docker/stage` writes the Dockerfile and layers without needing Docker. CI (`.github/workflows/ci.yml`) runs on push to `main` (then deploys) and on pull requests (checks only).

## Deploy (Fly.io, private)
The app runs at https://ride-the-lightning.fly.dev behind a browser login (D63, D64). It's one always-on machine in Amsterdam, reading the live feed.
```bash
fly auth login                                   # once; flyctl comes from .mise.toml
fly secrets set BASIC_AUTH_USER=... BASIC_AUTH_PASSWORD=...   # in your own terminal, not in a chat
sbt docker/Docker/stage
fly deploy target/out/jvm/scala-3.9.0/docker/docker/stage --config "$PWD/fly.toml" --ha=false --remote-only
fly logs -a ride-the-lightning                   # look for "Last minute: N strikes"
```
CI deploys on every push to `main` once all checks pass (D65). There it uses `--local-only` instead of `--remote-only`: the runner has Docker, so flyctl builds the image there, reusing the layers from the check build, and pushes it to `registry.fly.io`. Either way Fly runs the same OCI image, booted as a Firecracker micro-VM rather than a Docker container; `USER`, `ENV` and `ENTRYPOINT` from the Dockerfile still apply. It needs a deploy token for this app only, stored as a GitHub secret. Piping it means it never shows on screen:
```bash
fly tokens create deploy -a ride-the-lightning --name github-actions | gh secret set FLY_API_TOKEN
```
`--config` needs an absolute path, because flyctl resolves it against the build directory. `--ha=false` keeps one machine; Fly's default of two would open two Blitzortung connections. Changing a secret restarts the machine. Every restart starts with an empty hour and downloads the shapes again (no volume).

## Files
```
project/plugins.sbt                     + sbt-native-packager 1.13.0
build.sbt                               docker project: buildUi resource generator, start script and image settings
backend/src/main/scala/rtl/http/HttpApi.scala  staticResourcesGetServerEndpoint for webapp/ on the classpath, after the API
backend/src/test/scala/rtl/http/HttpApiTest.scala  / serves the webapp, unknown paths 404, the API wins
backend/src/test/resources/webapp/index.html     stand-in for the built UI in tests
docker-compose.yml                      one service, geo volume, ./data read-only for replay
.github/workflows/ci.yml                tests, UI checks, image build, then fly deploy on main
fly.toml                                Fly.io app: one always-on 1 GB machine, health check, REQUIRE_BASIC_AUTH
backend/src/main/scala/rtl/http/HttpConfig.scala  BasicAuthConfig: off / on / required, constant-time check
backend/src/main/scala/rtl/admin/AdminApi.scala   health is a public endpoint, version is not
```

## How it fits together
```
sbt docker/Docker/publishLocal
  docker project ── dependsOn ──> backend (classes + dependency jars)
     └─ Compile / resourceGenerators: buildUi
          pnpm install --frozen-lockfile, pnpm build ──> ui/dist ──copy──> resource_managed/webapp/
  └─> rtl.docker-0.1.0-SNAPSHOT.jar (just webapp/**) + backend jar + libs + bin/ride-the-lightning
  └─> Dockerfile (eclipse-temurin:25-jre, user 1001) ──docker build──> ride-the-lightning:latest

request ──> Netty ──> /api/v1/... endpoints ──> Swagger ──> webapp/ files ──> 404
```

## Concepts
- **Several sbt projects in one build**: `build.sbt` now has `backend`, `docker` and `root`. `docker` has no sources. It `dependsOn(backend)`, so its classpath includes the backend, and its only job is packaging. `root` aggregates `backend` only: a command run at the root (`compile`, `testFull`) goes to the aggregated projects, so pnpm never runs during normal builds. You call `docker/...` explicitly. It's like a separate packaging project in a .NET solution that you build only for release.
- **Resource generators**: `Compile / resourceGenerators += buildUi.taskValue` registers a task that produces resource files, which sbt adds to the jar next to `src/main/resources`. `buildUi` shells out with `scala.sys.process.Process(cmd, dir).!` (the `!` runs it and returns the exit code), then copies `ui/dist` into `resourceManaged/webapp`. `Def.uncached` tells sbt 2 not to cache the task's result, because its inputs (the UI sources) live outside what sbt tracks. Without it, sbt could replay an old result and skip the build.
- **Classpath resources**: `webapp/index.html` inside a jar is read via `ClassLoader.getResource("webapp/index.html")`, the same way `application.conf` is found. tapir-files' `staticResourcesGetServerEndpoint(emptyInput)(classLoader, "webapp")` maps the rest of the path to a resource. It serves `index.html` for `/`, sets `Content-Type`, `ETag` and `Last-Modified`, and answers `If-None-Match` with 304. It's listed after the API endpoints. Netty tries endpoints in order, so `/api/v1/health` never reaches it. In dev and in plain backend tests there's no `webapp/` on the classpath, so `/` is a 404. `HttpApiTest` puts a fake one in `src/test/resources`.
- **No SPA fallback** (D60): Bootzooka serves `index.html` for any unknown path, because its React router owns URLs like `/login`. We have no client routes, and that fallback would turn a mistyped `/api/v1/stat` into a 200 HTML page, which makes API bugs harder to spot. Unknown paths are a 404 instead. The body is empty, because the files endpoint answers before our JSON error handler sees the request.
- **sbt-native-packager, Universal vs Docker**: `JavaServerAppPackaging` builds a "universal" layout: `lib/` (all jars), `bin/ride-the-lightning` (a bash start script that builds the classpath and runs `rtl.Main`) and `conf/application.ini` (default arguments). `Universal / javaOptions` with a `-J` prefix become JVM flags in that file; without the prefix they'd be passed to the app. This is how the `.jvmopts` flags get into the image. `DockerPlugin` wraps the same layout in a Dockerfile. `Docker/stage` writes it and the layers; `Docker/publishLocal` also runs `docker build`.
- **The generated Dockerfile**: it's a two-stage build. Stage 0 copies the layers and makes them read-only (`chmod u=rX,g=rX`). The final stage creates the user `demiourgos728` (uid 1001, group root), copies the files and switches to `USER 1001:0`, so the app never runs as root. Layers are split by how often they change (dependency jars vs our jars), so a code change rebuilds only a small layer. Look at `target/out/jvm/scala-3.9.0/docker/docker/stage/Dockerfile`.
- **Patching the Dockerfile from sbt**: `dockerCommands` is the list of Dockerfile lines as values (`Cmd`, `ExecCmd`). We insert one `RUN install -d -o 1001 -g 0 /opt/docker/data/geo` before the last `USER` line, which is still root at that point. That's needed because of how volumes start (next point).
- **Volumes and ownership**: a named volume (`geo:` in compose) starts empty. On first mount Docker copies the image's content at that path into it, including the owner. If the directory didn't exist in the image, Docker would create it as root, and uid 1001 couldn't write the shapes there. A bind mount (`./data:/recordings:ro`) instead shows the host folder as is, with the host's uids. That's fine for reading the recording, but it's why `data/geo` isn't bind-mounted.
- **Config from the environment**: nothing in the Scala code knows about Docker. `application.conf` already had `host = ${?HTTP_HOST}`-style overrides (pureconfig/HOCON: use the env var if set). The image sets `HTTP_HOST=0.0.0.0`, because `localhost` inside a container isn't reachable through the published port, and `GEO_CACHE_DIR`. Compose sets `INGEST_SOURCE` and `REPLAY_FILE`. `${INGEST_SOURCE:-blitzortung}` in compose takes the value from your shell, or the default.
- **Graceful stop**: `docker compose stop` sends SIGTERM to PID 1, which is `java` because the start script `exec`s it. The JVM runs its shutdown hooks, so `OxApp` interrupts the main scope, the forks (ingest, the logger) and the Netty server, and the websocket closes. That's the same path as Ctrl+C in dev.
- **CI with mise**: `jdx/mise-action` reads `.mise.toml`, so CI uses the same Java, sbt, Node and pnpm as your machine, from one source of truth. `actions/cache` keeps the coursier/sbt downloads, keyed by the build files. It also keeps `data/geo`, keyed by `NaturalEarth.scala`, which holds the pinned URLs, so CI doesn't download the shapes from GitHub on every run. The last step builds the image, which also proves `pnpm build` (with `tsc -b`) passes. Nothing is pushed.

- **Basic auth as a Tapir security input**: `auth.basic[UsernamePassword](challenge)` reads `Authorization: Basic <base64 user:pass>`. `.mapDecode` turns it into `Unit`: right credentials decode to `Value(())`, wrong ones to `Missing`. Because the failing input is an auth input, Tapir's default decode-failure handler answers 401 with `WWW-Authenticate: Basic realm="Ride the Lightning"`, and that header makes the browser show its login dialog. `prependSecurityIn(login)` adds it to every endpoint except `publicEndpoints` (health), without touching the endpoint definitions. The browser then sends the credentials on every same-origin request, `fetch` and `EventSource` included, so the UI code doesn't change. Like ASP.NET's authentication middleware with `[AllowAnonymous]` on the health check.
- **Constant-time comparison**: `MessageDigest.isEqual` always compares every byte. A plain `==` returns at the first difference, and with enough requests the response time leaks how many leading characters were right.
- **Fail closed**: `REQUIRE_BASIC_AUTH=true` (in `fly.toml`) makes `BasicAuthConfig`'s `require` throw at startup when no password is set. The app crashes instead of serving the map to everyone. A crash loop shows up in `fly logs`; an open app wouldn't.
- **Heap in a container**: the JVM sizes its default heap from the container's memory limit, at 25%. `-XX:MaxRAMPercentage=60` gives 600 MB of a 1 GB machine, and the rest is left for metaspace, threads and Netty buffers.

## Try
1. `docker image inspect ride-the-lightning --format '{{.Config.User}} {{.Config.Env}}'`: uid 1001 and the two env vars.
2. `docker compose exec ride-the-lightning ls -l /opt/docker/data/geo`: the shapes, owned by 1001.
3. `curl -s -D - -o /dev/null localhost:8080/` shows the `ETag`; repeat with `-H 'If-None-Match: "<etag>"'` and get a 304. (`curl -I` sends `HEAD`, which only GET endpoints answer with 405.)
4. Change the title in `ui/index.html`, run `sbt docker/Docker/publishLocal` and `docker compose up -d`, and reload. Then run `sbt compile` and notice that pnpm didn't run.
5. Remove the `dockerCommands` patch, rebuild and run `docker volume rm ride-the-lightning_geo`. The first start fails to write the shapes: the volume is owned by root.
