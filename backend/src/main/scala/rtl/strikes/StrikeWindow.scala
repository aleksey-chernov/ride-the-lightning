package rtl.strikes

import ox.discard
import java.time.{Clock, Duration}
import java.util.concurrent.atomic.AtomicReference

/** Strikes from the last `window`, by strike time. One writer (ingest), many readers (HTTP). */
class StrikeWindow(window: Duration, clock: Clock):
  private val strikes = AtomicReference(Vector.empty[Strike])

  def add(strike: Strike): Unit =
    val cutoff = clock.instant().minus(window)
    // arrival order is only roughly time order, so dropWhile may leave a few stale ones; reads filter them
    strikes.updateAndGet(current => (current :+ strike).dropWhile(_.time.isBefore(cutoff))).discard

  def recent(within: Duration): Vector[Strike] =
    val cutoff = clock.instant().minus(within)
    strikes.get().filterNot(_.time.isBefore(cutoff))

  def snapshot: Vector[Strike] = recent(window)
