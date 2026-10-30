package rtl.strikes

import rtl.geo.Place

import java.time.Instant

/** A single lightning strike, independent of the data source.
  *
  * `time` has microsecond precision (truncated when parsed), so live, stored and restored copies of a strike match.
  * `stations` counts stations whose signal was used (`status & 4`) among the at most 40 the feed reports, so it is a
  * lower bound when that list is full. `mds` is the timing spread in ns (0 when missing). Both are stored with the
  * strike but not served by the API.
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
