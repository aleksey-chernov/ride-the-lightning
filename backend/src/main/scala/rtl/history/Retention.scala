package rtl.history

import ox.*
import rtl.Logging

import java.time.{Clock, Duration}
import scala.concurrent.duration.*
import scala.util.control.NonFatal

/** Deletes stored strikes older than `keep`: once at start, then every `interval`. `run` blocks; Main forks it. */
class Retention(repository: StrikeRepository, keep: Duration, clock: Clock, interval: FiniteDuration = 1.hour)
    extends Logging:
  def cleanUp(): Int = repository.deleteOlderThan(clock.instant().minus(keep))

  def run(): Unit = forever {
    try logger.info(s"Retention: deleted ${cleanUp()} strikes older than ${keep.toDays} days")
    catch case NonFatal(e) => logger.warn(s"Retention failed: ${e.getMessage}")
    sleep(interval)
  }
