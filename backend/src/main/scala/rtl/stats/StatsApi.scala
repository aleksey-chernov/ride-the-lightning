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
    statsEndpoint.handleSuccess { maxMds =>
      Stats.compute(window.snapshot, counters.received.get, counters.duplicates.get, clock.instant(), maxMds)
    }
  )

object StatsApi:
  val statsEndpoint = baseEndpoint.get
    .in("stats")
    .in(
      query[Option[Int]]("maxMds")
        .validateOption(Validator.inRange(1, 1_000_000))
        .description("Keep only strikes with 0 < mds <= maxMds (timing spread in ns); omit to keep all")
    )
    .out(jsonBody[StatsResponse])
    .description(
      "Counts per minute, place and 0.5° hotspot plus detection percentiles, computed from the in-memory window; " +
        "`maxMds` keeps strikes whose timing spread is at most that many ns"
    )
    .tag("stats")
