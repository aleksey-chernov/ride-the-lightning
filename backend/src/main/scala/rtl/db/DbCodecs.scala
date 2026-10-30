package rtl.db

import com.augustnagro.magnum.DbCodec

import java.time.Instant
import java.time.temporal.ChronoUnit.MICROS

object DbCodecs:
  /** SQLite has no timestamp type: instants are stored as epoch microseconds (INTEGER). */
  given DbCodec[Instant] = DbCodec[Long].biMap(Instant.EPOCH.plus(_, MICROS), MICROS.between(Instant.EPOCH, _))
