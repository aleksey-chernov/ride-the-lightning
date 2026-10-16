package rtl.admin

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.*
import sttp.tapir.server.stub4.TapirSyncStubInterpreter
import rtl.http.{HttpApi, HttpConfig}

class AdminApiTest extends AnyFlatSpec with Matchers:
  private val httpApi = HttpApi(AdminApi().endpoints, HttpConfig("localhost", 8080))

  // Runs endpoint logic in-process; no real server or network.
  private val backend = TapirSyncStubInterpreter().whenServerEndpointsRunLogic(httpApi.allEndpoints).backend()

  "GET /api/v1/health" should "return ok" in {
    val response = basicRequest.get(uri"http://test/api/v1/health").send(backend)
    response.code.code shouldBe 200
    response.body shouldBe Right("""{"status":"ok"}""")
  }

  "GET /api/v1/version" should "return build info" in {
    val response = basicRequest.get(uri"http://test/api/v1/version").send(backend)
    response.body.toOption.get should include("\"name\":\"ride-the-lightning\"")
  }

  "unknown path" should "return a JSON 404" in {
    val response = basicRequest.get(uri"http://test/api/v1/nope").send(backend)
    response.code.code shouldBe 404
  }
