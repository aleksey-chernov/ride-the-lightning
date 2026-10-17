package rtl.strikes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ox.*
import rtl.TestClock
import rtl.TestStrikes.strike

import scala.concurrent.duration.*

class StrikeBroadcasterTest extends AnyFlatSpec with Matchers:
  private val t = TestClock().now

  private def awaitSubscribers(b: StrikeBroadcaster, n: Int): Unit =
    while b.subscriberCount < n do sleep(5.millis)

  "StrikeBroadcaster" should "deliver strikes published after subscribing to every subscriber" in supervised {
    val broadcaster = StrikeBroadcaster()
    val a = fork(broadcaster.flow.take(2).runToList())
    val b = fork(broadcaster.flow.take(2).runToList())
    awaitSubscribers(broadcaster, 2)

    broadcaster.publish(strike(t, lat = 1))
    broadcaster.publish(strike(t, lat = 2))

    a.join().map(_.lat) shouldBe List(1, 2)
    b.join().map(_.lat) shouldBe List(1, 2)
  }

  it should "unsubscribe when a flow ends" in supervised {
    val broadcaster = StrikeBroadcaster()
    val a = fork(broadcaster.flow.take(1).runToList())
    awaitSubscribers(broadcaster, 1)
    broadcaster.publish(strike(t))
    a.join().discard
    broadcaster.subscriberCount shouldBe 0
  }

  it should "never block the publisher, even with a stalled subscriber" in supervised {
    val broadcaster = StrikeBroadcaster(bufferSize = 2)
    // stalls on the first strike; interrupted when the scope ends
    fork(broadcaster.flow.map(_ => never).runDrain()).discard
    awaitSubscribers(broadcaster, 1)

    timeout(1.second)((1 to 10_000).foreach(_ => broadcaster.publish(strike(t))))
  }
