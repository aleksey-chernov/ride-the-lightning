package rtl.strikes

import rtl.geo.Place

import java.time.Instant

/** A single lightning strike, independent of the data source. */
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
