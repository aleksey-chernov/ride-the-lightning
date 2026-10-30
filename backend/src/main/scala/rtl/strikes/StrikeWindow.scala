package rtl.strikes

import ox.discard
import java.time.{Clock, Duration, Instant}
import java.util.concurrent.atomic.AtomicReference

/** Strikes from the last `window`, by strike time. One writer (ingest), many readers (HTTP). */
class StrikeWindow(window: Duration, clock: Clock):
  private val strikes = AtomicReference(Vector.empty[Strike])

  /** Strikes before this instant are outside the window. */
  def cutoff: Instant = clock.instant().minus(window)

  def add(strike: Strike): Unit =
    val start = cutoff
    // arrival order is only roughly time order, so dropWhile may leave a few stale ones; reads filter them
    strikes.updateAndGet(current => (current :+ strike).dropWhile(_.time.isBefore(start))).discard

  /** Adds many strikes in one update (e.g. restored from history), keeping the window in time order. */
  def addAll(more: Seq[Strike]): Unit =
    val start = cutoff
    strikes.updateAndGet(current => (current ++ more).sortBy(_.time).dropWhile(_.time.isBefore(start))).discard

  def recent(within: Duration): Vector[Strike] =
    val cutoff = clock.instant().minus(within)
    strikes.get().filterNot(_.time.isBefore(cutoff))

  def snapshot: Vector[Strike] = recent(window)
