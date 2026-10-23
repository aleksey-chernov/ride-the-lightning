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

  it should "name each 1° hotspot after its most common place" in {
    stats.hotspots.head shouldBe Hotspot(41.5, 2.5, "Spain", 3)
    stats.hotspots.map(h => (h.place, h.count)) shouldBe List("Spain" -> 3, "Gulf of Mexico" -> 1)
  }
