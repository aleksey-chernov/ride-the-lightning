package rtl.stats

import com.github.plokhotnyuk.jsoniter_scala.macros.ConfiguredJsonValueCodec
import rtl.http.ResponseContract.given
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.validate
import sttp.tapir.Validator

import java.time.Instant

case class MinuteCount(minute: Instant, count: Int) derives Schema
case class PlaceCount(name: String, @validate(Validator.enumeration(List("country", "sea"))) kind: String, count: Int)
    derives Schema
case class Hotspot(lat: Double, lon: Double, place: String, count: Int)
    derives Schema // center of a 0.5° cell; most common place in it
case class Percentiles(p50: Double, p90: Double, p99: Double) derives Schema // nearest-rank
case class Detection(delaySeconds: Option[Percentiles]) derives Schema // last 10 minutes; None when empty

case class StatsResponse(
    generatedAt: Instant,
    received: Long,
    duplicatesDropped: Long,
    lastMinute: Int,
    last10Minutes: Int,
    lastHour: Int,
    perMinute: List[MinuteCount], // last 60 minutes, oldest first
    places: List[PlaceCount], // last 10 minutes, top 10
    hotspots: List[Hotspot], // last 10 minutes, top 10
    detection: Detection
) derives ConfiguredJsonValueCodec,
      Schema
