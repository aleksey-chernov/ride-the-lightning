package rtl.ingest

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ox.*
import ox.flow.Flow
import rtl.TestClock
import rtl.TestStrikes.strike
import rtl.geo.{Place, PlaceIndex, PlaceKind}
import rtl.history.StrikeWriter
import rtl.strikes.{Deduplicator, Strike, StrikeBroadcaster, StrikeWindow}

import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class IngestTest extends AnyFlatSpec with Matchers:
  private lazy val places = PlaceIndex.load(Path.of("data/geo")) // the real Natural Earth data, downloaded once
  private val spain = Place("Spain", PlaceKind.Country)

  private class Fixture(live: Strike*):
    val clock = TestClock()
    val window = StrikeWindow(Duration.ofMinutes(60), clock)
    val counters = IngestCounters()
    val stored = ConcurrentLinkedQueue[Strike]()
    val writer = StrikeWriter(_.foreach(stored.add), gather = 10.millis)
    val source = new StrikeSource:
      def strikes: Flow[Strike] = Flow.fromValues(live*)
    val ingest = Ingest(source, Deduplicator(clock), places, window, StrikeBroadcaster(), counters, Some(writer))

  "restore" should "put stored strikes back into the window with their places" in {
    val f = Fixture()
    val t = f.clock.now
    f.ingest.restore(Seq(strike(t.minusSeconds(30), lat = 41.39, lon = 2.17))) shouldBe 1
    f.window.snapshot.map(_.place) shouldBe Vector(Some(spain))
  }

  it should "seed dedupe with the last minute, so late copies of restored strikes are dropped" in supervised {
    val t = TestClock().now
    val recent = strike(t.minusSeconds(20), lat = 41.39, lon = 2.17)
    val older = strike(t.minusSeconds(120), lat = 41.39, lon = 2.17)
    val fresh = strike(t.minusSeconds(5), lat = 30, lon = -40)
    val f = Fixture(recent.copy(region = 2), older.copy(region = 2), fresh)
    f.ingest.restore(Seq(older, recent))
    fork(f.writer.run()).discard

    f.ingest.run()
    timeout(5.seconds)(while f.writer.written.get < 2 do sleep(5.millis))
    f.counters.duplicates.get shouldBe 1 // the copy of `recent`; `older` is past the dedupe memory
    f.stored.asScala.map(_.time).toList shouldBe List(older.time, fresh.time)
  }
