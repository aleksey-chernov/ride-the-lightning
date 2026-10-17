package rtl.strikes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import rtl.TestClock
import rtl.TestStrikes.strike

import java.time.Duration

class DeduplicatorTest extends AnyFlatSpec with Matchers:
  "Deduplicator" should "drop an exact repeat from another region" in {
    val clock = TestClock()
    val dedup = Deduplicator(clock)
    dedup.isNew(strike(clock.now, region = 8)) shouldBe true
    dedup.isNew(strike(clock.now, region = 9)) shouldBe false
  }

  it should "drop a slightly different solution of the same strike" in {
    val dedup = Deduplicator(TestClock())
    val t = TestClock().now
    dedup.isNew(strike(t, 41.5690, 2.1047)) shouldBe true
    dedup.isNew(strike(t.plusNanos(12_400), 41.5623, 2.1147)) shouldBe false // 12 µs, <0.02°
  }

  it should "keep strikes that are far apart in time or space" in {
    val dedup = Deduplicator(TestClock())
    val t = TestClock().now
    dedup.isNew(strike(t)) shouldBe true
    dedup.isNew(strike(t.plusMillis(5))) shouldBe true
    dedup.isNew(strike(t, lat = 51.0)) shouldBe true
  }

  it should "forget strikes after its memory window" in {
    val clock = TestClock()
    val dedup = Deduplicator(clock, memory = Duration.ofMinutes(1))
    val s = strike(clock.now)
    dedup.isNew(s) shouldBe true
    clock.advance(Duration.ofMinutes(2))
    dedup.isNew(s) shouldBe true
  }
