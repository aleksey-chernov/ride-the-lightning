package rtl

import java.time.{Clock, Duration, Instant, ZoneId, ZoneOffset}

/** A clock tests can move forward by hand. */
class TestClock(var now: Instant = Instant.parse("2026-10-17T12:00:30Z")) extends Clock:
  def advance(d: Duration): Unit = now = now.plus(d)
  override def instant(): Instant = now
  override def getZone: ZoneId = ZoneOffset.UTC
  override def withZone(zone: ZoneId): Clock = this
