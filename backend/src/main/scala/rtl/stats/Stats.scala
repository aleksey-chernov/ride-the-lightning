package rtl.stats

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

    val regions = last10
      .groupMapReduce(_.region)(_ => 1)(_ + _)
      .toList
      .map((region, count) => RegionCount(region, count))
      .sortBy(-_.count)

    val hotspots = last10
      .groupMapReduce(s => (math.floor(s.lat), math.floor(s.lon)))(_ => 1)(_ + _)
      .toList
      .sortBy(-_._2)
      .take(10)
      .map { case ((lat, lon), count) => Hotspot(lat + 0.5, lon + 0.5, count) }

    StatsResponse(now, received, duplicates, since(1).size, last10.size, since(60).size, perMinute, regions, hotspots)
