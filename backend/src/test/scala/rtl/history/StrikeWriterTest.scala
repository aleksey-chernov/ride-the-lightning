package rtl.history

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ox.*
import rtl.TestClock
import rtl.TestStrikes.strike
import rtl.strikes.Strike

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class StrikeWriterTest extends AnyFlatSpec with Matchers:
  private val t = TestClock().now

  private def awaitWritten(writer: StrikeWriter, n: Long): Unit =
    timeout(5.seconds)(while writer.written.get < n do sleep(5.millis))

  "StrikeWriter" should "write offered strikes in batches of at most maxBatch" in supervised {
    val batches = ConcurrentLinkedQueue[Seq[Strike]]()
    val writer = StrikeWriter(batches.add(_).discard, maxBatch = 4, gather = 10.millis)
    (1 to 10).foreach(i => writer.offer(strike(t, lat = i)))
    fork(writer.run()).discard

    awaitWritten(writer, 10)
    batches.asScala.map(_.size).toList shouldBe List(4, 4, 2)
    batches.asScala.flatten.map(_.lat).toList shouldBe (1 to 10).map(_.toDouble).toList
  }

  it should "drop and count strikes when the queue is full, without blocking" in {
    val writer = StrikeWriter(_ => (), capacity = 2)
    (1 to 5).foreach(_ => writer.offer(strike(t)))
    writer.dropped.get shouldBe 3
  }

  it should "count a failed batch as dropped and keep going" in supervised {
    val written = ConcurrentLinkedQueue[Strike]()
    val writer = StrikeWriter(
      batch => if batch.exists(_.lat == 1) then throw RuntimeException("disk full") else batch.foreach(written.add),
      gather = 10.millis,
      afterFailure = 10.millis
    )
    writer.offer(strike(t, lat = 1))
    fork(writer.run()).discard
    timeout(5.seconds)(while writer.dropped.get < 1 do sleep(5.millis))
    writer.offer(strike(t, lat = 2))

    awaitWritten(writer, 1)
    writer.dropped.get shouldBe 1
    written.asScala.map(_.lat).toList shouldBe List(2.0)
  }
