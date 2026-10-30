package rtl.admin

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.*
import sttp.tapir.server.stub4.TapirSyncStubInterpreter
import rtl.BuildInfo
import rtl.http.{HttpApi, HttpConfig}

class AdminApiTest extends AnyFlatSpec with Matchers:
  private val admin = AdminApi()
  private val httpApi = HttpApi[Any](admin.endpoints, HttpConfig("localhost", 8080), admin.publicEndpoints)

  // Runs endpoint logic in-process; no real server or network.
  private val backend = TapirSyncStubInterpreter().whenServerEndpointsRunLogic(httpApi.allEndpoints).backend()

  "GET /api/v1/health" should "return ok" in {
    val response = basicRequest.get(uri"http://test/api/v1/health").send(backend)
    response.code.code shouldBe 200
    response.body shouldBe Right("""{"status":"ok"}""")
  }

  "GET /api/v1/version" should "return build info" in {
    val response = basicRequest.get(uri"http://test/api/v1/version").send(backend)
    val body = response.body.toOption.get
    body should include("\"name\":\"ride-the-lightning\"")
    body should include(s"\"version\":\"${BuildInfo.version}\"")
    body should include(s"\"commit\":\"${BuildInfo.commit}\"")
  }

  "unknown path" should "return a JSON 404" in {
    val response = basicRequest.get(uri"http://test/api/v1/nope").send(backend)
    response.code.code shouldBe 404
  }
