package rtl.strikes

import ox.discard
import java.time.{Clock, Duration, Instant}
import scala.collection.mutable

/** Drops strikes already seen: Blitzortung repeats a strike per overlapping region, sometimes re-solved slightly
  * differently. Not thread-safe; used only by the ingest fork.
  */
class Deduplicator(clock: Clock, memory: Duration = Duration.ofMinutes(1)):
  private val seen = mutable.Queue.empty[(Instant, Strike)] // (seenAt, strike)

  def isNew(strike: Strike): Boolean =
    val now = clock.instant()
    while seen.nonEmpty && seen.head._1.isBefore(now.minus(memory)) do seen.dequeue().discard
    val duplicate = seen.exists((_, other) => Deduplicator.sameStrike(strike, other))
    if !duplicate then seen.enqueue((now, strike))
    !duplicate

object Deduplicator:
  val MaxTimeDiff: Duration = Duration.ofMillis(1)
  val MaxDegreesDiff = 0.02 // ~2 km

  def sameStrike(a: Strike, b: Strike): Boolean =
    Duration.between(a.time, b.time).abs.compareTo(MaxTimeDiff) < 0 &&
      math.abs(a.lat - b.lat) < MaxDegreesDiff &&
      math.abs(a.lon - b.lon) < MaxDegreesDiff
