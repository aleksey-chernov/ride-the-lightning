package rtl.ingest

import ox.discard
import rtl.geo.PlaceIndex
import rtl.history.StrikeWriter
import rtl.strikes.{Deduplicator, Strike, StrikeBroadcaster, StrikeWindow}

import java.util.concurrent.atomic.AtomicLong

class IngestCounters:
  val received = AtomicLong()
  val duplicates = AtomicLong()

/** The ingest pipeline: source → dedupe → classify place → window + live subscribers (+ history, when stored). `run`
  * blocks; Main runs it in a fork.
  */
class Ingest(
    source: StrikeSource,
    deduplicator: Deduplicator,
    places: PlaceIndex,
    window: StrikeWindow,
    broadcaster: StrikeBroadcaster,
    counters: IngestCounters,
    writer: Option[StrikeWriter] = None
):
  def run(): Unit = source.strikes.runForeach { strike =>
    counters.received.incrementAndGet().discard
    if deduplicator.isNew(strike) then
      val located = locate(strike)
      window.add(located)
      broadcaster.publish(located)
      writer.foreach(_.offer(located))
    else counters.duplicates.incrementAndGet().discard
  }

  /** Puts stored strikes back into the window (not broadcast, not stored again); call before `run`. */
  def restore(strikes: Seq[Strike]): Int =
    val located = strikes.map(locate)
    window.addAll(located)
    deduplicator.remember(located)
    located.size

  private def locate(strike: Strike): Strike = strike.copy(place = Some(places.locate(strike.lat, strike.lon)))
