package rtl.admin

import com.github.plokhotnyuk.jsoniter_scala.macros.ConfiguredJsonValueCodec
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.json.jsoniter.*
import sttp.tapir.server.ServerEndpoint
import rtl.BuildInfo
import rtl.http.Http.baseEndpoint

class AdminApi:
  import AdminApi.*

  // health stays open behind basic auth, for the hosting platform's checks
  val publicEndpoints: List[ServerEndpoint[Any, Identity]] = List(
    healthEndpoint.handleSuccess(_ => HealthResponse("ok"))
  )

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    versionEndpoint.handleSuccess(_ => VersionResponse(BuildInfo.name, BuildInfo.version, BuildInfo.scalaVersion))
  )

object AdminApi:
  val healthEndpoint = baseEndpoint.get.in("health").out(jsonBody[HealthResponse]).tag("admin")
  val versionEndpoint = baseEndpoint.get.in("version").out(jsonBody[VersionResponse]).tag("admin")

  case class HealthResponse(status: String) derives ConfiguredJsonValueCodec, Schema
  case class VersionResponse(name: String, version: String, scalaVersion: String)
      derives ConfiguredJsonValueCodec,
        Schema
