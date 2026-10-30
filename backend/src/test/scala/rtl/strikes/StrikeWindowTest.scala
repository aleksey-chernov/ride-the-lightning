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

  "addAll" should "merge strikes in time order and drop those older than the window" in {
    val clock = TestClock()
    val window = StrikeWindow(Duration.ofMinutes(60), clock)
    window.add(strike(clock.now.minus(Duration.ofMinutes(10)), lat = 2))
    window.addAll(
      Seq(
        strike(clock.now.minus(Duration.ofMinutes(5)), lat = 3),
        strike(clock.now.minus(Duration.ofMinutes(61)), lat = 0),
        strike(clock.now.minus(Duration.ofMinutes(30)), lat = 1)
      )
    )

    window.snapshot.map(_.lat) shouldBe Vector(1, 2, 3)
  }
