# Phase 1: backend skeleton

## Run it
```bash
sbt                     # start the sbt shell once (keeps JVM warm)
> ~backend/reStart      # compile + start server, restart on every save
> backend/reStop        # stop it
> test                  # run tests
```
In IntelliJ (shared configs in `.idea/runConfigurations/`):
- **Backend**: run/debug `rtl.Main` (module `ride-the-lightning.backend.main`); use Debug for breakpoints.
- **Backend (watch)**: `~backend/reStart`, restarts on save.

Then:
- http://localhost:8080/api/v1/health → `{"status":"ok"}`
- http://localhost:8080/api/v1/version
- http://localhost:8080/api/v1/docs → Swagger UI

## Files
```
build.sbt                      build definition (Scala code!)
project/build.properties       sbt version
project/plugins.sbt            sbt plugins: buildinfo, scalafmt
backend/src/main/
  resources/application.conf   config (HOCON), env overrides via ${?VAR}
  resources/logback.xml        logging format/levels
  scala/rtl/
    Main.scala                 entry point (OxApp)
    Dependencies.scala         wiring: builds all components
    Logging.scala              mixin giving each class a `logger`
    config/Config.scala        typed config loaded by pureconfig
    http/Http.scala            base endpoint + JSON error type
    http/HttpApi.scala         Netty server, /api/v1 prefix, Swagger
    http/HttpConfig.scala      host/port config
    admin/AdminApi.scala       /health and /version endpoints
backend/src/test/scala/rtl/admin/AdminApiTest.scala
```

## How a request flows
`Main` → `Config.load()` → `Dependencies.create(config)` builds `AdminApi` and `HttpApi` → `httpApi.start()` binds Netty on :8080.
A `GET /api/v1/health` hits Netty → Tapir strips `/api/v1`, matches `healthEndpoint` → runs `handleSuccess` logic → `HealthResponse("ok")` encoded to JSON by jsoniter.

## Scala concepts used
- **`object`**: a singleton. `object Main` is the program entry point; `object AdminApi` (a *companion object*) holds the static-like parts of `class AdminApi`.
- **`case class`**: immutable data class with `equals`, `toString`, `copy` and pattern matching for free. `HealthResponse(status: String)`.
- **`derives`**: asks the compiler to generate type-class instances. `derives ConfigReader` (read from config), `derives ConfiguredJsonValueCodec` (JSON), `derives Schema` (OpenAPI docs). No reflection; it's all compile time.
- **`trait`**: like an interface that can hold code. `Logging` is mixed in with `with Logging`.
- **Significant indentation**: Scala 3 allows Python-like blocks without braces (`:` then indent).
- **`using` / context parameters**: `def run(using Ox)` receives an `Ox` scope implicitly. `httpApi.start()` requires it too, which proves at compile time that the server runs inside a scope that will clean it up.
- **Imports**: `import sttp.tapir.*` imports everything from a package (Scala 3 uses `*`, not `_`).

## Libraries
- **Tapir**: endpoints are *values* (`baseEndpoint.get.in("health").out(jsonBody[HealthResponse])`). The same description drives the server, the Swagger docs, and (later) clients/tests. Logic is attached separately with `.handleSuccess`.
- **Ox**: direct-style concurrency on Java virtual threads. `OxApp.Simple` gives a root scope; `never` blocks until Ctrl+C, then everything started in the scope is shut down. No `Future`/`IO` wrappers.
- **pureconfig**: maps `application.conf` onto `Config` case classes; fails at startup with a clear message if something is missing.
- **jsoniter-scala**: fast JSON codecs generated at compile time.
- **sbt-buildinfo**: generates `rtl.BuildInfo` object with name/version at build time.

## Things worth knowing
- **sbt 2 client/server**: `sbt` connects to a running sbt server if one exists (IntelliJ may start one during import). Use the IntelliJ sbt shell *or* a terminal `sbt`, not both at once.
- **`reStart`** is a small custom task in `build.sbt` (same as Bootzooka): stop the previous background run, then `bgRun`. With `~` it re-runs on file changes.
- **Tests** use `TapirSyncStubInterpreter`: the endpoints run in-process, no real port is opened.
- Compare with Bootzooka: [Main.scala](https://github.com/softwaremill/bootzooka/blob/master/backend/src/main/scala/com/softwaremill/bootzooka/Main.scala), [HttpApi.scala](https://github.com/softwaremill/bootzooka/blob/master/backend/src/main/scala/com/softwaremill/bootzooka/http/HttpApi.scala), [VersionApi.scala](https://github.com/softwaremill/bootzooka/blob/master/backend/src/main/scala/com/softwaremill/bootzooka/admin/VersionApi.scala). We dropped OTEL, MDC, the `Fail` error ADT, static webapp serving (comes in Phase 9) and macwire.

## Try
1. Add a `GET /api/v1/ping` returning `"pong"` as text: `baseEndpoint.get.in("ping").out(stringBody)`; add it to `AdminApi.endpoints` and watch `~reStart` pick it up and Swagger show it.
2. Run with another port: `HTTP_PORT=9090 sbt backend/run`.
3. Break the config (rename `port` in `application.conf`) and read pureconfig's error.
