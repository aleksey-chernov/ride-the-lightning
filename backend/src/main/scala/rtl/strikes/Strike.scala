package rtl.strikes

import rtl.geo.Place

import java.time.Instant

/** A single lightning strike, independent of the data source.
  *
  * `stations` counts stations whose signal was used (`status & 4`) among the at most 40 the feed reports, so it is a
  * lower bound when that list is full. `mds` is the timing spread in ns (0 when missing).
  */
case class Strike(
    time: Instant,
    lat: Double,
    lon: Double,
    region: Int,
    stations: Int,
    delaySeconds: Double,
    mds: Int,
    place: Option[Place] = None // set by Ingest; sources don't classify
)
