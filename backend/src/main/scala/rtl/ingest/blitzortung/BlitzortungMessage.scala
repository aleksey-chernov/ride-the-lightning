package rtl.ingest.blitzortung

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.ConfiguredJsonValueCodec
import rtl.strikes.Strike

import java.time.Instant
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
    time = Instant.ofEpochSecond(0, time),
    lat = lat + latc,
    lon = lon + lonc,
    region = region,
    stations = sig.size,
    delaySeconds = delay,
    mds = mds
  )

case class BlitzortungStation(sta: Int, lat: Double, lon: Double) derives ConfiguredJsonValueCodec

object BlitzortungMessage:
  def parse(json: String): Either[Throwable, BlitzortungMessage] = Try(
    readFromString[BlitzortungMessage](json)
  ).toEither
