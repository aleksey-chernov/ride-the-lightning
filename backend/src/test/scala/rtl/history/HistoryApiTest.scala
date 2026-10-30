package rtl.history

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import rtl.TestDb
import rtl.TestStrikes.strike
import rtl.http.{HttpApi, HttpConfig}
import sttp.client4.*
import sttp.tapir.server.stub4.TapirSyncStubInterpreter

import java.nio.file.Path
import java.time.Instant
import scala.concurrent.duration.*

class HistoryApiTest extends AnyFlatSpec with Matchers with TestDb:
  private val config = HistoryConfig(enabled = true, Path.of("unused.db"), keep = 7.days, maxStrikes = 100_000)
  private val repository = StrikeRepository(db)
  private val t0 = Instant.parse("2026-10-17T12:00:00Z")

  private def backendFor(api: HistoryApi) = TapirSyncStubInterpreter()
    .whenServerEndpointsRunLogic(HttpApi[Any](api.endpoints, HttpConfig("localhost", 8080)).allEndpoints)
    .backend()

  private val backend = backendFor(HistoryApi(Some(repository), config))
  private val offBackend = backendFor(HistoryApi(None, config))

  private def get(path: String, b: SyncBackend = backend) =
    basicRequest.get(uri"http://test/api/v1/history/$path").send(b)

  private def getStrikes(from: String, to: String, b: SyncBackend = backend) =
    basicRequest.get(uri"http://test/api/v1/history/strikes?from=$from&to=$to").send(b)

  "GET /history/range" should "report the stored range" in {
    repository.insert(Seq(strike(t0.plusSeconds(1)), strike(t0.plusSeconds(9))))
    get("range").body shouldBe Right(
      """{"enabled":true,"oldest":"2026-10-17T12:00:01Z","newest":"2026-10-17T12:00:09Z","keepDays":7}"""
    )
  }

  it should "omit oldest and newest when nothing is stored" in {
    get("range").body shouldBe Right("""{"enabled":true,"keepDays":7}""")
  }

  it should "say history is off without a repository" in {
    get("range", offBackend).body shouldBe Right("""{"enabled":false,"keepDays":7}""")
  }

  "GET /history/strikes" should "return the half-open range, oldest first" in {
    repository.insert(Seq(strike(t0.plusSeconds(2), lat = 2), strike(t0, lat = 1), strike(t0.plusSeconds(60), lat = 3)))
    getStrikes("2026-10-17T12:00:00Z", "2026-10-17T12:01:00Z").body shouldBe Right(
      """{"from":"2026-10-17T12:00:00Z","to":"2026-10-17T12:01:00Z","step":1,"strikes":[""" +
        """{"timeMs":1792238400000,"lat":1.0,"lon":10.0},""" +
        """{"timeMs":1792238402000,"lat":2.0,"lon":10.0}]}"""
    )
  }

  it should "return an empty strikes array for an empty range" in {
    getStrikes("2026-10-17T12:00:00Z", "2026-10-17T13:00:00Z").body shouldBe Right(
      """{"from":"2026-10-17T12:00:00Z","to":"2026-10-17T13:00:00Z","step":1,"strikes":[]}"""
    )
  }

  it should "reject `to` not after `from`" in {
    val response = getStrikes("2026-10-17T12:00:00Z", "2026-10-17T12:00:00Z")
    response.code.code shouldBe 400
    response.body.left.toOption.get should include("after")
  }

  it should "reject a range longer than keep" in {
    getStrikes("2026-10-01T12:00:00Z", "2026-10-17T12:00:00Z").code.code shouldBe 400
  }

  it should "reject an instant too far out to store" in {
    getStrikes("+300000-01-01T00:00:00Z", "+300000-01-02T00:00:00Z").code.code shouldBe 400
  }

  it should "reject a malformed instant" in {
    getStrikes("yesterday", "2026-10-17T12:00:00Z").code.code shouldBe 400
  }

  it should "answer 503 when history is off" in {
    getStrikes("2026-10-17T12:00:00Z", "2026-10-17T13:00:00Z", offBackend).code.code shouldBe 503
  }
