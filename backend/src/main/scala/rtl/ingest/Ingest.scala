package rtl.ingest

import ox.discard
import rtl.geo.PlaceIndex
import rtl.strikes.{Deduplicator, StrikeBroadcaster, StrikeWindow}

import java.util.concurrent.atomic.AtomicLong

class IngestCounters:
  val received = AtomicLong()
  val duplicates = AtomicLong()

/** The ingest pipeline: source → dedupe → classify place → window + live subscribers. `run` blocks; Main runs it in a
  * fork.
  */
class Ingest(
    source: StrikeSource,
    deduplicator: Deduplicator,
    places: PlaceIndex,
    window: StrikeWindow,
    broadcaster: StrikeBroadcaster,
    counters: IngestCounters
):
  def run(): Unit = source.strikes.runForeach { strike =>
    counters.received.incrementAndGet().discard
    if deduplicator.isNew(strike) then
      val located = strike.copy(place = Some(places.locate(strike.lat, strike.lon)))
      window.add(located)
      broadcaster.publish(located)
    else counters.duplicates.incrementAndGet().discard
  }
