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
      place: Option[Place] = None,
      stations: Int = 20,
      delaySeconds: Double = 5.0,
      mds: Int = 5000
  ): Strike =
    Strike(time, lat, lon, region, stations, delaySeconds, mds, place)
