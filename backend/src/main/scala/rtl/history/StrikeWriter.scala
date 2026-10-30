package rtl.history

import ox.*
import rtl.Logging
import rtl.strikes.Strike

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** Stores strikes in batches off the ingest path. `offer` never blocks: when the queue is full (storage slow or
  * failing), the strike is dropped and counted. `run` blocks; Main runs it in a fork.
  */
class StrikeWriter(
    write: Seq[Strike] => Unit,
    capacity: Int = 10_000,
    maxBatch: Int = 500,
    gather: FiniteDuration = 1.second,
    afterFailure: FiniteDuration = 5.seconds
) extends Logging:
  private val queue = ArrayBlockingQueue[Strike](capacity)
  val written = AtomicLong()
  val dropped = AtomicLong()

  def offer(strike: Strike): Unit = if !queue.offer(strike) then dropped.incrementAndGet().discard

  def run(): Unit = forever {
    val batch = java.util.ArrayList[Strike]()
    batch.add(queue.take()).discard // waits for the first strike
    queue.drainTo(batch, maxBatch - 1).discard
    val ok =
      try
        write(batch.asScala.toSeq)
        written.addAndGet(batch.size).discard
        true
      catch
        case NonFatal(e) =>
          dropped.addAndGet(batch.size).discard
          logger.warn(s"Dropped ${batch.size} strikes, storing them failed: ${e.getMessage}")
          false
    // outside the try, so interruption on shutdown isn't swallowed; also gathers ~1 s of strikes per transaction
    sleep(if ok then gather else afterFailure)
  }
