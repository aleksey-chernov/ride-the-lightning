package rtl.history

import com.augustnagro.magnum.*
import ox.discard
import rtl.db.Db
import rtl.db.DbCodecs.given
import rtl.strikes.Strike

import java.time.Instant

private case class StrikeRow(
    time: Instant,
    lat: Double,
    lon: Double,
    region: Int,
    stations: Int,
    delaySeconds: Double,
    mds: Int
) derives DbCodec:
  def toStrike: Strike = Strike(time, lat, lon, region, stations, delaySeconds, mds)

case class PlaybackRow(time: Instant, lat: Double, lon: Double) derives DbCodec

/** Rows of a range; `step` = 1 when every strike is there, n when only every n-th is. */
case class PlaybackPage(rows: Vector[PlaybackRow], step: Int)

/** Stored strikes. Every time bound comes from the caller (the app clock), never from `now` in SQL. */
class StrikeRepository(db: Db):
  def insert(strikes: Seq[Strike]): Unit = db.transact {
    batchUpdate(strikes) { s =>
      sql"""INSERT INTO strikes (time_us, lat, lon, region, stations, delay_seconds, mds)
            VALUES (${s.time}, ${s.lat}, ${s.lon}, ${s.region}, ${s.stations}, ${s.delaySeconds}, ${s.mds})""".update
    }.discard
  }

  /** Strikes at or after `from`, oldest first, without places. */
  def since(from: Instant): Vector[Strike] = db.transact {
    sql"""SELECT time_us, lat, lon, region, stations, delay_seconds, mds FROM strikes
          WHERE time_us >= $from ORDER BY time_us""".query[StrikeRow].run().map(_.toStrike)
  }

  /** Strikes in [from, to), oldest first; above `max` rows only every `step`-th one. */
  def page(from: Instant, to: Instant, max: Int): PlaybackPage = db.transact {
    val count = sql"SELECT count(*) FROM strikes WHERE time_us >= $from AND time_us < $to".query[Int].run().head
    val step = math.max(1, (count + max - 1) / max)
    val rows = sql"""SELECT time_us, lat, lon FROM (
                       SELECT time_us, lat, lon, row_number() OVER (ORDER BY time_us) AS n
                       FROM strikes WHERE time_us >= $from AND time_us < $to
                     ) WHERE n % $step = 0 ORDER BY time_us""".query[PlaybackRow].run()
    PlaybackPage(rows, step)
  }

  /** Oldest and newest stored strike times; None when nothing is stored. */
  def range(): Option[(Instant, Instant)] = db.transact {
    val (oldest, newest) =
      sql"SELECT min(time_us), max(time_us) FROM strikes".query[(Option[Instant], Option[Instant])].run().head
    oldest.zip(newest)
  }

  def deleteOlderThan(cutoff: Instant): Int = db.transact(sql"DELETE FROM strikes WHERE time_us < $cutoff".update.run())
