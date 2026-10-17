package rtl.strikes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import rtl.TestClock
import rtl.TestStrikes.strike

import java.time.Duration

class StrikeWindowTest extends AnyFlatSpec with Matchers:
  "StrikeWindow" should "return only strikes inside the requested period" in {
    val clock = TestClock()
    val window = StrikeWindow(Duration.ofMinutes(60), clock)
    window.add(strike(clock.now.minus(Duration.ofMinutes(30))))
    window.add(strike(clock.now.minus(Duration.ofMinutes(5))))
    window.add(strike(clock.now))

    window.snapshot should have size 3
    window.recent(Duration.ofMinutes(10)) should have size 2
  }

  it should "evict strikes older than the window as time passes" in {
    val clock = TestClock()
    val window = StrikeWindow(Duration.ofMinutes(60), clock)
    window.add(strike(clock.now))
    clock.advance(Duration.ofMinutes(61))
    window.add(strike(clock.now))

    window.snapshot.map(_.time) shouldBe Vector(clock.now)
  }
