package rtl.stats

import rtl.http.Http.baseEndpoint
import rtl.ingest.IngestCounters
import rtl.strikes.StrikeWindow
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.json.jsoniter.*
import sttp.tapir.server.ServerEndpoint

import java.time.Clock

class StatsApi(window: StrikeWindow, counters: IngestCounters, clock: Clock):
  import StatsApi.*

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    statsEndpoint.handleSuccess { _ =>
      Stats.compute(window.snapshot, counters.received.get, counters.duplicates.get, clock.instant())
    }
  )

object StatsApi:
  val statsEndpoint = baseEndpoint.get
    .in("stats")
    .out(jsonBody[StatsResponse])
    .description("Counts per minute, place and 1° hotspot, computed from the in-memory window")
    .tag("stats")
