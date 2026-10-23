package rtl.stats

import rtl.geo.{Place, PlaceKind}
import rtl.strikes.Strike

import java.time.temporal.ChronoUnit.MINUTES
import java.time.{Duration, Instant}

object Stats:
  /** Pure: same inputs, same output, so it's trivial to test. */
  def compute(strikes: Vector[Strike], received: Long, duplicates: Long, now: Instant): StatsResponse =
    def since(minutes: Int) = strikes.filterNot(_.time.isBefore(now.minus(Duration.ofMinutes(minutes))))
    val last10 = since(10)

    val byMinute = strikes.groupMapReduce(_.time.truncatedTo(MINUTES))(_ => 1)(_ + _)
    val currentMinute = now.truncatedTo(MINUTES)
    val perMinute = (59 to 0 by -1).toList.map { ago =>
      val minute = currentMinute.minus(ago, MINUTES)
      MinuteCount(minute, byMinute.getOrElse(minute, 0))
    }

    val places = last10
      .groupMapReduce(placeOf)(_ => 1)(_ + _)
      .toList
      .sortBy((place, count) => (-count, place.name))
      .take(10)
      .map((place, count) => PlaceCount(place.name, place.kind.toString.toLowerCase, count))

    val hotspots = last10
      .groupBy(s => (math.floor(s.lat), math.floor(s.lon)))
      .toList
      .sortBy((_, inCell) => -inCell.size)
      .take(10)
      .map { case ((lat, lon), inCell) =>
        Hotspot(lat + 0.5, lon + 0.5, mostCommon(inCell.map(placeOf)).name, inCell.size)
      }

    StatsResponse(now, received, duplicates, since(1).size, last10.size, since(60).size, perMinute, places, hotspots)

  private val Unclassified = Place("Unknown", PlaceKind.Sea)

  private def placeOf(s: Strike): Place = s.place.getOrElse(Unclassified)

  private def mostCommon(places: Vector[Place]): Place =
    places.groupMapReduce(identity)(_ => 1)(_ + _).maxBy((place, count) => (count, place.name))._1
