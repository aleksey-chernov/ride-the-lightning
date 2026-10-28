package rtl

import ox.*
import rtl.admin.AdminApi
import rtl.config.Config
import rtl.geo.PlaceIndex
import rtl.http.HttpApi
import rtl.ingest.blitzortung.BlitzortungSource
import rtl.ingest.{Ingest, IngestCounters, ReplaySource, SourceKind, StrikeSource}
import rtl.stats.StatsApi
import rtl.strikes.{Deduplicator, StrikeBroadcaster, StrikeWindow, StrikesApi}
import sttp.client4.DefaultSyncBackend
import sttp.tapir.server.netty.sync.OxStreams

import java.time.Clock
import scala.jdk.DurationConverters.*

// Manual wiring: build every component here, passing dependencies via constructors.
case class Dependencies(httpApi: HttpApi[OxStreams], ingest: Ingest, window: StrikeWindow, counters: IngestCounters)

object Dependencies:
  def create(config: Config, clock: Clock = Clock.systemUTC())(using Ox): Dependencies =
    val strikeSource: StrikeSource = config.ingest.source match
      case SourceKind.Blitzortung =>
        // closed automatically when the enclosing scope ends
        val backend = useCloseableInScope(DefaultSyncBackend())
        BlitzortungSource(config.ingest.blitzortung, backend)
      case SourceKind.Replay => ReplaySource(config.ingest.replay)

    val window = StrikeWindow(config.strikes.window.toJava, clock)
    val broadcaster = StrikeBroadcaster()
    val counters = IngestCounters()
    val places = PlaceIndex.load(config.geo.cacheDir) // downloads ~4 MB of Natural Earth shapes on the first start
    val ingest = Ingest(strikeSource, Deduplicator(clock), places, window, broadcaster, counters)

    val admin = AdminApi()
    val endpoints =
      admin.endpoints ++ StrikesApi(window, broadcaster).endpoints ++ StatsApi(window, counters, clock).endpoints
    Dependencies(HttpApi(endpoints, config.http, admin.publicEndpoints), ingest, window, counters)
