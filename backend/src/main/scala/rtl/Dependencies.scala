package rtl

import ox.*
import rtl.admin.AdminApi
import rtl.config.Config
import rtl.db.Db
import rtl.geo.PlaceIndex
import rtl.history.{HistoryApi, HistoryConfig, Retention, StrikeRepository, StrikeWriter}
import rtl.http.HttpApi
import rtl.ingest.blitzortung.BlitzortungSource
import rtl.ingest.{Ingest, IngestCounters, ReplaySource, SourceKind, StrikeSource}
import rtl.stats.StatsApi
import rtl.strikes.{Deduplicator, StrikeBroadcaster, StrikeWindow, StrikesApi}
import sttp.client4.DefaultSyncBackend
import sttp.tapir.server.netty.sync.OxStreams

import java.time.Clock
import scala.jdk.DurationConverters.*
import scala.util.control.NonFatal

// Manual wiring: build every component here, passing dependencies via constructors.
case class Dependencies(
    httpApi: HttpApi[OxStreams],
    ingest: Ingest,
    window: StrikeWindow,
    counters: IngestCounters,
    storage: Option[Storage]
)

/** Present when live strikes are stored: history on and the live source (replay times are fake). */
case class Storage(repository: StrikeRepository, writer: StrikeWriter, retention: Retention)

object Dependencies extends Logging:
  def create(config: Config, clock: Clock = Clock.systemUTC())(using Ox): Dependencies =
    val strikeSource: StrikeSource = config.ingest.source match
      case SourceKind.Blitzortung =>
        // closed automatically when the enclosing scope ends
        val backend = useCloseableInScope(DefaultSyncBackend())
        BlitzortungSource(config.ingest.blitzortung, backend)
      case SourceKind.Replay => ReplaySource(config.ingest.replay)

    val history = openHistory(config.history)
    val storage = history.flatMap { repository =>
      if config.ingest.source == SourceKind.Replay then
        logger.info("Replay mode: history is read-only")
        None
      else
        val keep = config.history.keep.toJava
        logger.info(s"History: storing strikes in ${config.history.file}, keeping ${keep.toDays} days")
        Some(Storage(repository, StrikeWriter(repository.insert), Retention(repository, keep, clock)))
    }

    val window = StrikeWindow(config.strikes.window.toJava, clock)
    val broadcaster = StrikeBroadcaster()
    val counters = IngestCounters()
    val places = PlaceIndex.load(config.geo.cacheDir) // downloads ~4 MB of Natural Earth shapes on the first start
    val ingest =
      Ingest(strikeSource, Deduplicator(clock), places, window, broadcaster, counters, storage.map(_.writer))

    val admin = AdminApi()
    val endpoints =
      admin.endpoints ++ StrikesApi(window, broadcaster).endpoints ++ StatsApi(window, counters, clock).endpoints ++
        HistoryApi(history, config.history).endpoints
    Dependencies(HttpApi(endpoints, config.http, admin.publicEndpoints), ingest, window, counters, storage)

  // the live view never depends on the database: if it can't be opened, run without history
  private def openHistory(config: HistoryConfig): Option[StrikeRepository] =
    if !config.enabled then
      logger.info("History is off (HISTORY_ENABLED=false)")
      None
    else
      try Some(StrikeRepository(Db.open(config.file)))
      catch
        case NonFatal(e) =>
          logger.error(s"History is off: can't open or migrate ${config.file}", e)
          None
