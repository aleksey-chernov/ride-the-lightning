package rtl.http

import ox.Ox
import sttp.capabilities.WebSockets
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.files.staticResourcesGetServerEndpoint
import sttp.tapir.model.UsernamePassword
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.server.netty.NettyConfig
import sttp.tapir.server.netty.sync.{NettySyncServer, NettySyncServerBinding, NettySyncServerOptions, OxStreams}
import sttp.tapir.swagger.SwaggerUIOptions
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import rtl.BuildInfo

/** Serves the API under `/api/v1`, Swagger UI under `/api/v1/docs` and the built UI (packaged image only) under `/`.
  *
  * `R` = capabilities the endpoints need: `OxStreams` in the app (SSE), `Any` (none) in stub-based tests. With basic
  * auth configured, everything except `publicEndpoints` asks for the password.
  */
class HttpApi[R >: OxStreams & WebSockets](
    apiEndpoints: List[ServerEndpoint[R, Identity]],
    config: HttpConfig,
    publicEndpoints: List[ServerEndpoint[R, Identity]] = Nil
):
  private val contextPath = List("api", "v1")

  // a wrong password decodes as "missing", so Tapir answers 401 with a challenge and the browser asks again
  private val login: EndpointInput[Unit] =
    if !config.auth.enabled then emptyInput
    else
      auth
        .basic[UsernamePassword](WWWAuthenticateChallenge.basic("Ride the Lightning"))
        .mapDecode(c => if config.auth.accepts(c) then DecodeResult.Value(()) else DecodeResult.Missing)(_ =>
          UsernamePassword("", None)
        )

  private val serverOptions: NettySyncServerOptions = NettySyncServerOptions.customiseInterceptors
    .defaultHandlers(msg => ValuedEndpointOutput(Http.jsonErrorOutput, ErrorResponse(msg)), notFoundWhenRejected = true)
    .corsInterceptor(CORSInterceptor.default[Identity])
    .options

  val allEndpoints: List[ServerEndpoint[R, Identity]] =
    val docs = SwaggerInterpreter(swaggerUIOptions = SwaggerUIOptions.default.copy(contextPath = contextPath))
      .fromServerEndpoints[Identity](publicEndpoints ++ apiEndpoints, "Ride the Lightning", BuildInfo.version)
    val prefix = contextPath.foldLeft(emptyInput: EndpointInput[Unit])(_ / _)
    // only the docker project puts `webapp/` on the classpath; in dev Vite serves the UI
    val webapp = staticResourcesGetServerEndpoint[Identity](emptyInput)(classOf[HttpApi[?]].getClassLoader, "webapp")
    publicEndpoints.map(_.prependSecurityIn(prefix)) ++
      ((apiEndpoints ++ docs).map(_.prependSecurityIn(prefix)) :+ webapp).map(_.prependSecurityIn(login))

  def start()(using Ox): NettySyncServerBinding =
    NettySyncServer(serverOptions, NettyConfig.default.host(config.host).port(config.port))
      .addEndpoints(allEndpoints)
      .start()
