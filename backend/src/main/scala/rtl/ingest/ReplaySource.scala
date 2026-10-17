package rtl.ingest

import ox.*
import ox.flow.Flow
import rtl.Logging
import rtl.ingest.blitzortung.BlitzortungMessage
import rtl.strikes.Strike

import java.nio.file.{Files, Path}
import java.time.{Duration, Instant}
import scala.jdk.CollectionConverters.*

/** Replays a recording (one decoded JSON message per line) in a loop, shifting strike times to "now". */
class ReplaySource(config: ReplayConfig) extends StrikeSource with Logging:
  def strikes: Flow[Strike] = Flow.usingEmit { emit =>
    val recorded = load()
    val first = recorded.head.time
    logger.info(s"Replaying ${recorded.size} strikes from ${config.file} at ${config.speed}x")
    forever {
      val loopStart = Instant.now()
      recorded.foreach { strike =>
        val offsetNanos = (Duration.between(first, strike.time).toNanos / config.speed).toLong
        val due = loopStart.plusNanos(offsetNanos)
        val wait = Duration.between(Instant.now(), due)
        if !wait.isNegative then sleep(scala.concurrent.duration.Duration.fromNanos(wait.toNanos))
        emit(strike.copy(time = due))
      }
    }
  }

  private def load(): Vector[Strike] =
    val strikes = Files
      .readAllLines(Path.of(config.file))
      .asScala
      .toVector
      .flatMap(BlitzortungMessage.parse(_).toOption)
      .map(_.toStrike)
      .sortBy(_.time)
    require(strikes.nonEmpty, s"No strikes in ${config.file}; record some first (see docs/phase-2.md)")
    strikes
