package rtl.stats

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import rtl.TestClock
import rtl.TestStrikes.strike
import rtl.http.{HttpApi, HttpConfig}
import rtl.ingest.IngestCounters
import rtl.strikes.StrikeWindow
import sttp.client4.*
import sttp.tapir.server.stub4.TapirSyncStubInterpreter

import java.time.Duration

class StatsApiTest extends AnyFlatSpec with Matchers:
  private val clock = TestClock()
  private val window = StrikeWindow(Duration.ofMinutes(60), clock)
  window.add(strike(clock.now.minus(Duration.ofMinutes(20))))
  window.add(strike(clock.now))

  private val backend =
    TapirSyncStubInterpreter()
      .whenServerEndpointsRunLogic(
        HttpApi[Any](StatsApi(window, IngestCounters(), clock).endpoints, HttpConfig("localhost", 8080)).allEndpoints
      )
      .backend()

  "GET /stats" should "return stats computed from the window" in {
    val body = basicRequest.get(uri"http://test/api/v1/stats").send(backend).body.toOption.get
    body should include("\"lastHour\":2")
  }
