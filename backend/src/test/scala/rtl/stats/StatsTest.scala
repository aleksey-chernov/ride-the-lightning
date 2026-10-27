package rtl.stats

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import rtl.TestStrikes.strike
import rtl.geo.{Place, PlaceKind}

import java.time.{Duration, Instant}

class StatsTest extends AnyFlatSpec with Matchers:
  private val now = Instant.parse("2026-10-17T12:00:30Z")
  private def ago(minutes: Long) = now.minus(Duration.ofMinutes(minutes))

  private val spain = Place("Spain", PlaceKind.Country)
  private val balearic = Place("Balearic Sea", PlaceKind.Sea)
  private val gulf = Place("Gulf of Mexico", PlaceKind.Sea)

  private val strikes = Vector(
    strike(ago(0), lat = 41.2, lon = 2.1, place = Some(spain)),
    strike(ago(0), lat = 41.7, lon = 2.9, place = Some(balearic)),
    strike(ago(1), lat = 41.4, lon = 2.2, place = Some(spain)),
    strike(ago(3), lat = 25.3, lon = -86.6, place = Some(gulf)),
    strike(ago(30), lat = 10.0, lon = 10.0, place = Some(Place("Nigeria", PlaceKind.Country)))
  )
  private val stats = Stats.compute(strikes, received = 6, duplicates = 2, now)

  "Stats.compute" should "count strikes per time range" in {
    (stats.lastMinute, stats.last10Minutes, stats.lastHour) shouldBe (3, 4, 5)
    (stats.received, stats.duplicatesDropped) shouldBe (6, 2)
  }

  it should "produce 60 zero-filled minutes, oldest first" in {
    stats.perMinute should have size 60
    stats.perMinute.last shouldBe MinuteCount(Instant.parse("2026-10-17T12:00:00Z"), 2)
    stats.perMinute.map(_.count).sum shouldBe 5
  }

  it should "rank places over the last 10 minutes, busiest first" in {
    stats.places shouldBe List(
      PlaceCount("Spain", "country", 2),
      PlaceCount("Balearic Sea", "sea", 1),
      PlaceCount("Gulf of Mexico", "sea", 1)
    )
  }

  it should "group 0.5° hotspots with floor, busiest first, ties by latitude then longitude" in {
    stats.hotspots shouldBe List(
      Hotspot(41.25, 2.25, "Spain", 2),
      Hotspot(25.25, -86.75, "Gulf of Mexico", 1),
      Hotspot(41.75, 2.75, "Balearic Sea", 1)
    )
  }

  it should "break hotspot ties by longitude within the same latitude" in {
    val sameLat = Vector(strike(ago(0), lat = 10.1, lon = 5.1), strike(ago(0), lat = 10.1, lon = -5.1))
    Stats.compute(sameLat, 0, 0, now).hotspots.map(h => (h.lat, h.lon)) shouldBe List((10.25, -5.25), (10.25, 5.25))
  }

  it should "not filter or echo anything without maxMds" in {
    (stats.maxMds, stats.filteredOut) shouldBe (None, 0)
  }

  it should "keep only strikes with 0 < mds <= maxMds and count the rest as filtered out" in {
    val noisy = strikes :+ strike(ago(0), mds = 9000) :+ strike(ago(2), mds = 0) :+ strike(ago(61), mds = 9000)
    val filtered = Stats.compute(noisy, received = 9, duplicates = 2, now, maxMds = Some(6000))
    (filtered.lastMinute, filtered.last10Minutes, filtered.lastHour) shouldBe (3, 4, 5)
    filtered.perMinute.map(_.count).sum shouldBe 5
    (filtered.maxMds, filtered.filteredOut, filtered.received) shouldBe (Some(6000), 2, 9)
    filtered.detection.mdsNanos.map(_.p99) shouldBe Some(5000.0)
  }

  it should "compute detection percentiles over the last 10 minutes only" in {
    val window = Vector(
      strike(ago(0), stations = 10, delaySeconds = 3.0, mds = 2000),
      strike(ago(5), stations = 30, delaySeconds = 25.0, mds = 9000),
      strike(ago(30), stations = 99, delaySeconds = 99.0, mds = 14000)
    )
    Stats.compute(window, 0, 0, now).detection shouldBe Detection(
      Some(Percentiles(10, 30, 30)),
      Some(Percentiles(3.0, 25.0, 25.0)),
      Some(Percentiles(2000, 9000, 9000))
    )
  }

  it should "leave missing delay and mds (0) out of the detection percentiles" in {
    val window = Vector(
      strike(ago(0), delaySeconds = 3.0, mds = 2000),
      strike(ago(1), delaySeconds = 0, mds = 0),
      strike(ago(2), delaySeconds = 0, mds = 0)
    )
    val detection = Stats.compute(window, 0, 0, now).detection
    (detection.delaySeconds, detection.mdsNanos) shouldBe (
      Some(Percentiles(3.0, 3.0, 3.0)),
      Some(Percentiles(2000, 2000, 2000))
    )
  }

  it should "omit detection percentiles when the last 10 minutes are empty" in {
    Stats.compute(Vector(strike(ago(30))), 0, 0, now).detection shouldBe Detection(None, None, None)
  }

  "Stats.percentiles" should "use nearest rank" in {
    Stats.percentiles((1 to 10).map(_.toDouble).toVector) shouldBe Some(Percentiles(5, 9, 10))
    Stats.percentiles((1 to 100).map(_.toDouble).reverse.toVector) shouldBe Some(Percentiles(50, 90, 99))
  }

  it should "return a single value for all three and None when empty" in {
    Stats.percentiles(Vector(7.5)) shouldBe Some(Percentiles(7.5, 7.5, 7.5))
    Stats.percentiles(Vector.empty) shouldBe None
  }
