package rtl.stats

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import rtl.TestStrikes.strike

import java.time.{Duration, Instant}

class StatsTest extends AnyFlatSpec with Matchers:
  private val now = Instant.parse("2026-10-17T12:00:30Z")
  private def ago(minutes: Long) = now.minus(Duration.ofMinutes(minutes))

  private val strikes = Vector(
    strike(ago(0), lat = 41.2, lon = 2.1, region = 8),
    strike(ago(0), lat = 41.7, lon = 2.9, region = 8),
    strike(ago(3), lat = 25.3, lon = -86.6, region = 3),
    strike(ago(30), lat = 10.0, lon = 10.0, region = 5)
  )
  private val stats = Stats.compute(strikes, received = 6, duplicates = 2, now)

  "Stats.compute" should "count strikes per time range" in {
    (stats.lastMinute, stats.last10Minutes, stats.lastHour) shouldBe (2, 3, 4)
    (stats.received, stats.duplicatesDropped) shouldBe (6, 2)
  }

  it should "produce 60 zero-filled minutes, oldest first" in {
    stats.perMinute should have size 60
    stats.perMinute.last shouldBe MinuteCount(Instant.parse("2026-10-17T12:00:00Z"), 2)
    stats.perMinute.map(_.count).sum shouldBe 4
  }

  it should "rank regions and 1° hotspots over the last 10 minutes" in {
    stats.regions shouldBe List(RegionCount(8, 2), RegionCount(3, 1))
    stats.hotspots.head shouldBe Hotspot(41.5, 2.5, 2)
    stats.hotspots.map(_.count) shouldBe List(2, 1)
  }
