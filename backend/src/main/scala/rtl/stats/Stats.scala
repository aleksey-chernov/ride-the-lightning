package rtl.stats

import rtl.geo.{Place, PlaceKind}
import rtl.strikes.Strike

import java.time.temporal.ChronoUnit.MINUTES
import java.time.{Duration, Instant}

object Stats:
  /** Pure: same inputs, same output, so it's trivial to test. `maxMds` keeps strikes with `0 < mds <= maxMds`. */
  def compute(
      strikes: Vector[Strike],
      received: Long,
      duplicates: Long,
      now: Instant,
      maxMds: Option[Int] = None
  ): StatsResponse =
    def since(minutes: Int)(in: Vector[Strike]) = in.filterNot(_.time.isBefore(now.minus(Duration.ofMinutes(minutes))))
    val kept = maxMds.fold(strikes)(max => strikes.filter(s => s.mds > 0 && s.mds <= max))
    val last10 = since(10)(kept)
    val lastHour = since(60)(kept)

    val byMinute = kept.groupMapReduce(_.time.truncatedTo(MINUTES))(_ => 1)(_ + _)
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
      .groupBy(s => (cell(s.lat), cell(s.lon)))
      .toList
      .sortBy { case ((lat, lon), inCell) => (-inCell.size, lat, lon) }
      .take(10)
      .map { case ((lat, lon), inCell) =>
        Hotspot(lat + CellDegrees / 2, lon + CellDegrees / 2, mostCommon(inCell.map(placeOf)).name, inCell.size)
      }

    val detection = Detection(
      percentiles(last10.map(_.stations.toDouble)),
      percentiles(last10.map(_.delaySeconds).filter(_ > 0)), // 0 means missing
      percentiles(last10.map(_.mds).filter(_ > 0).map(_.toDouble))
    )

    StatsResponse(
      generatedAt = now,
      received = received,
      duplicatesDropped = duplicates,
      lastMinute = since(1)(kept).size,
      last10Minutes = last10.size,
      lastHour = lastHour.size,
      perMinute = perMinute,
      places = places,
      hotspots = hotspots,
      maxMds = maxMds,
      filteredOut = since(60)(strikes).size - lastHour.size,
      detection = detection
    )

  /** Nearest-rank: the smallest value with at least p% of values at or below it. */
  private[stats] def percentiles(values: Vector[Double]): Option[Percentiles] = Option.when(values.nonEmpty) {
    val sorted = values.sorted
    def at(p: Int) = sorted(math.ceil(p * sorted.size / 100.0).toInt - 1)
    Percentiles(at(50), at(90), at(99))
  }

  private val CellDegrees = 0.5

  private def cell(x: Double) = math.floor(x / CellDegrees) * CellDegrees // floor, so -86.6 -> -87.0

  private val Unclassified = Place("Unknown", PlaceKind.Sea)

  private def placeOf(s: Strike): Place = s.place.getOrElse(Unclassified)

  private def mostCommon(places: Vector[Place]): Place =
    places.groupMapReduce(identity)(_ => 1)(_ + _).maxBy((place, count) => (count, place.name))._1
