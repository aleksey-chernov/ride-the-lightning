package rtl

import rtl.geo.Place
import rtl.strikes.Strike

import java.time.Instant

object TestStrikes:
  def strike(
      time: Instant,
      lat: Double = 50.0,
      lon: Double = 10.0,
      region: Int = 1,
      place: Option[Place] = None
  ): Strike =
    Strike(time, lat, lon, region, stations = 20, delaySeconds = 5.0, mds = 5000, place = place)
