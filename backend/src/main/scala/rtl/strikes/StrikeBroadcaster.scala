package rtl.strikes

import ox.*
import ox.flow.Flow
import rtl.Logging

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ArrayBlockingQueue, ConcurrentHashMap}

/** Fans strikes out to live subscribers. `publish` never blocks: a full subscriber queue drops the strike. */
class StrikeBroadcaster(bufferSize: Int = 1000) extends Logging:
  private class Subscriber:
    val queue = ArrayBlockingQueue[Strike](bufferSize)
    val dropped = AtomicLong()

  private val subscribers = ConcurrentHashMap.newKeySet[Subscriber]()

  def publish(strike: Strike): Unit =
    subscribers.forEach(s => if !s.queue.offer(strike) then s.dropped.incrementAndGet().discard)

  def subscriberCount: Int = subscribers.size

  /** Strikes published while this flow runs; unsubscribes when the flow ends or is interrupted. */
  def flow: Flow[Strike] = Flow.usingEmit { emit =>
    val subscriber = Subscriber()
    subscribers.add(subscriber).discard
    logger.info(s"Stream client connected ($subscriberCount active)")
    try forever(emit(subscriber.queue.take()))
    finally
      subscribers.remove(subscriber).discard
      logger.info(s"Stream client disconnected, ${subscriber.dropped.get} dropped ($subscriberCount active)")
  }
