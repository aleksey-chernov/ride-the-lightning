package rtl.http

import ox.Ox
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.server.netty.NettyConfig
import sttp.tapir.server.netty.sync.{NettySyncServer, NettySyncServerBinding, NettySyncServerOptions}
import sttp.tapir.swagger.SwaggerUIOptions
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import rtl.BuildInfo

/** Serves the API under `/api/v1` and Swagger UI under `/api/v1/docs`. */
class HttpApi(apiEndpoints: List[ServerEndpoint[Any, Identity]], config: HttpConfig):
  private val contextPath = List("api", "v1")

  private val serverOptions: NettySyncServerOptions = NettySyncServerOptions.customiseInterceptors
    .defaultHandlers(msg => ValuedEndpointOutput(Http.jsonErrorOutput, ErrorResponse(msg)), notFoundWhenRejected = true)
    .corsInterceptor(CORSInterceptor.default[Identity])
    .options

  val allEndpoints: List[ServerEndpoint[Any, Identity]] =
    val docs = SwaggerInterpreter(swaggerUIOptions = SwaggerUIOptions.default.copy(contextPath = contextPath))
      .fromServerEndpoints[Identity](apiEndpoints, "Ride the Lightning", BuildInfo.version)
    val prefix = contextPath.foldLeft(emptyInput: EndpointInput[Unit])(_ / _)
    (apiEndpoints ++ docs).map(_.prependSecurityIn(prefix))

  def start()(using Ox): NettySyncServerBinding =
    NettySyncServer(serverOptions, NettyConfig.default.host(config.host).port(config.port))
      .addEndpoints(allEndpoints)
      .start()
