package rtl.strikes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import rtl.TestClock
import rtl.TestStrikes.strike
import rtl.http.{HttpApi, HttpConfig}
import sttp.client4.*
import sttp.tapir.server.stub4.TapirSyncStubInterpreter

import java.time.Duration

class StrikesApiTest extends AnyFlatSpec with Matchers:
  private val clock = TestClock()
  private val window = StrikeWindow(Duration.ofMinutes(60), clock)
  window.add(strike(clock.now.minus(Duration.ofMinutes(20)), lat = 1.0))
  window.add(strike(clock.now, lat = 2.0))

  private val endpoints = StrikesApi(window, StrikeBroadcaster()).restEndpoints
  private val backend =
    TapirSyncStubInterpreter()
      .whenServerEndpointsRunLogic(HttpApi[Any](endpoints, HttpConfig("localhost", 8080)).allEndpoints)
      .backend()

  "GET /strikes/recent" should "return strikes from the default 15 minutes, newest first" in {
    val body = basicRequest.get(uri"http://test/api/v1/strikes/recent").send(backend).body.toOption.get
    body should include("\"lat\":2.0")
    body should not include "\"lat\":1.0"
  }

  it should "reject an out-of-range query parameter" in {
    basicRequest.get(uri"http://test/api/v1/strikes/recent?minutes=0").send(backend).code.code shouldBe 400
  }

  "an empty window" should "still return the strikes array" in {
    val emptyApi = StrikesApi(StrikeWindow(Duration.ofMinutes(60), TestClock()), StrikeBroadcaster()).restEndpoints
    val emptyBackend = TapirSyncStubInterpreter()
      .whenServerEndpointsRunLogic(HttpApi[Any](emptyApi, HttpConfig("localhost", 8080)).allEndpoints)
      .backend()
    basicRequest.get(uri"http://test/api/v1/strikes/recent").send(emptyBackend).body shouldBe Right(
      """{"strikes":[]}"""
    )
  }
