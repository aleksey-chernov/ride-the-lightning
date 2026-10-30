package rtl.ingest.blitzortung

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.ConfiguredJsonValueCodec
import rtl.strikes.Strike

import java.time.Instant
import java.time.temporal.ChronoUnit.MICROS
import scala.util.Try

/** Decoded Blitzortung JSON; unknown fields are ignored, optional ones default to 0. */
case class BlitzortungMessage(
    time: Long, // epoch nanoseconds
    lat: Double,
    lon: Double,
    latc: Double = 0,
    lonc: Double = 0,
    pol: Int = 0,
    mds: Int = 0,
    mcg: Int = 0,
    status: Int = 0,
    region: Int = 0,
    delay: Double = 0,
    sig: List[BlitzortungStation] = Nil
) derives ConfiguredJsonValueCodec:
  def toStrike: Strike = Strike(
    time = Instant.ofEpochSecond(0, time).truncatedTo(MICROS), // µs, the precision we store
    lat = lat + latc,
    lon = lon + lonc,
    region = region,
    stations = sig.count(_.used),
    delaySeconds = delay,
    mds = mds
  )

case class BlitzortungStation(sta: Int, lat: Double, lon: Double, status: Int = 0) derives ConfiguredJsonValueCodec:
  def used: Boolean = (status & 4) != 0 // status & 4 (the compendium's bit 3): signal used to compute the position

object BlitzortungMessage:
  def parse(json: String): Either[Throwable, BlitzortungMessage] = Try(
    readFromString[BlitzortungMessage](json)
  ).toEither
