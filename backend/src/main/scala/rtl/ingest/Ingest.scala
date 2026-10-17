package rtl.ingest

import ox.discard
import rtl.strikes.{Deduplicator, StrikeWindow}

import java.util.concurrent.atomic.AtomicLong

class IngestCounters:
  val received = AtomicLong()
  val duplicates = AtomicLong()

/** The ingest pipeline: source → dedupe → window. `run` blocks; Main runs it in a fork. */
class Ingest(source: StrikeSource, deduplicator: Deduplicator, window: StrikeWindow, counters: IngestCounters):
  def run(): Unit = source.strikes.runForeach { strike =>
    counters.received.incrementAndGet().discard
    if deduplicator.isNew(strike) then window.add(strike)
    else counters.duplicates.incrementAndGet().discard
  }
