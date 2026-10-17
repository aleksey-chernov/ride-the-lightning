package rtl.ingest

import ox.discard
import rtl.strikes.{Deduplicator, StrikeBroadcaster, StrikeWindow}

import java.util.concurrent.atomic.AtomicLong

class IngestCounters:
  val received = AtomicLong()
  val duplicates = AtomicLong()

/** The ingest pipeline: source → dedupe → window + live subscribers. `run` blocks; Main runs it in a fork. */
class Ingest(
    source: StrikeSource,
    deduplicator: Deduplicator,
    window: StrikeWindow,
    broadcaster: StrikeBroadcaster,
    counters: IngestCounters
):
  def run(): Unit = source.strikes.runForeach { strike =>
    counters.received.incrementAndGet().discard
    if deduplicator.isNew(strike) then
      window.add(strike)
      broadcaster.publish(strike)
    else counters.duplicates.incrementAndGet().discard
  }
