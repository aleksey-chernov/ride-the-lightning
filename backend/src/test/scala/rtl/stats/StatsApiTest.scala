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
  window.add(strike(clock.now.minus(Duration.ofMinutes(20)), mds = 12000))
  window.add(strike(clock.now, mds = 5000))

  private val backend =
    TapirSyncStubInterpreter()
      .whenServerEndpointsRunLogic(
        HttpApi[Any](StatsApi(window, IngestCounters(), clock).endpoints, HttpConfig("localhost", 8080)).allEndpoints
      )
      .backend()

  private def get(maxMds: Option[Int]) = basicRequest.get(uri"http://test/api/v1/stats?maxMds=$maxMds").send(backend)

  "GET /stats" should "return unfiltered stats without maxMds" in {
    val body = get(None).body.toOption.get
    body should include("\"lastHour\":2")
    body should include("\"filteredOut\":0")
    body should not include "\"maxMds\""
  }

  it should "apply and echo a valid maxMds" in {
    val body = get(Some(10000)).body.toOption.get
    body should include("\"lastHour\":1")
    body should include("\"maxMds\":10000")
    body should include("\"filteredOut\":1")
  }

  it should "reject maxMds=0" in {
    get(Some(0)).code.code shouldBe 400
  }
