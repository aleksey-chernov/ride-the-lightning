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

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    healthEndpoint.handleSuccess(_ => Health_OUT("ok")),
    versionEndpoint.handleSuccess(_ => Version_OUT(BuildInfo.name, BuildInfo.version, BuildInfo.scalaVersion))
  )

object AdminApi:
  val healthEndpoint = baseEndpoint.get.in("health").out(jsonBody[Health_OUT]).tag("admin")
  val versionEndpoint = baseEndpoint.get.in("version").out(jsonBody[Version_OUT]).tag("admin")

  case class Health_OUT(status: String) derives ConfiguredJsonValueCodec, Schema
  case class Version_OUT(name: String, version: String, scalaVersion: String) derives ConfiguredJsonValueCodec, Schema
