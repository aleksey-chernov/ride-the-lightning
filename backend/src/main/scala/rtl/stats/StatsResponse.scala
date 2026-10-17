package rtl.stats

import com.github.plokhotnyuk.jsoniter_scala.macros.ConfiguredJsonValueCodec
import sttp.tapir.Schema

import java.time.Instant

case class MinuteCount(minute: Instant, count: Int) derives Schema
case class RegionCount(region: Int, count: Int) derives Schema
case class Hotspot(lat: Double, lon: Double, count: Int) derives Schema // center of a 1° cell

case class StatsResponse(
    generatedAt: Instant,
    received: Long,
    duplicatesDropped: Long,
    lastMinute: Int,
    last10Minutes: Int,
    lastHour: Int,
    perMinute: List[MinuteCount], // last 60 minutes, oldest first
    regions: List[RegionCount], // last 10 minutes
    hotspots: List[Hotspot] // last 10 minutes, top 10
) derives ConfiguredJsonValueCodec,
      Schema
