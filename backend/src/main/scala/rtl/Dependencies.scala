package rtl

import ox.*
import rtl.admin.AdminApi
import rtl.config.Config
import rtl.http.HttpApi
import rtl.ingest.blitzortung.BlitzortungSource
import rtl.ingest.{ReplaySource, SourceKind, StrikeSource}
import sttp.client4.DefaultSyncBackend

// Manual wiring: build every component here, passing dependencies via constructors.
case class Dependencies(httpApi: HttpApi, strikeSource: StrikeSource)

object Dependencies:
  def create(config: Config)(using Ox): Dependencies =
    val adminApi = AdminApi()
    val strikeSource = config.ingest.source match
      case SourceKind.Blitzortung =>
        // closed automatically when the enclosing scope ends
        val backend = useCloseableInScope(DefaultSyncBackend())
        BlitzortungSource(config.ingest.blitzortung, backend)
      case SourceKind.Replay => ReplaySource(config.ingest.replay)
    Dependencies(HttpApi(adminApi.endpoints, config.http), strikeSource)
