package rtl.http

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import rtl.admin.AdminApi
import sttp.client4.*
import sttp.tapir.server.stub4.TapirSyncStubInterpreter

// src/test/resources/webapp stands in for the UI that the docker project bundles
class HttpApiTest extends AnyFlatSpec with Matchers:
  private val admin = AdminApi()

  private def backendWith(auth: BasicAuthConfig) =
    TapirSyncStubInterpreter()
      .whenServerEndpointsRunLogic(
        HttpApi[Any](admin.endpoints, HttpConfig("localhost", 8080, auth), admin.publicEndpoints).allEndpoints
      )
      .backend()

  private val open = backendWith(BasicAuthConfig.Off)
  private val locked = backendWith(BasicAuthConfig("me", "secret"))

  private def get(path: String) = basicRequest.get(uri"http://test".withWholePath(path))

  "the webapp" should "be served at /" in {
    get("/").send(open).body.toOption.get should include("test webapp")
  }

  it should "not answer for unknown paths" in {
    get("/nope").send(open).code.code shouldBe 404
    get("/api/v1/nope").send(open).code.code shouldBe 404
  }

  "the API" should "still win over the webapp" in {
    get("/api/v1/health").send(open).code.code shouldBe 200
  }

  "basic auth" should "challenge requests without credentials" in {
    val response = get("/").send(locked)
    response.code.code shouldBe 401
    response.header("WWW-Authenticate") shouldBe Some("""Basic realm="Ride the Lightning"""")
    get("/api/v1/version").send(locked).code.code shouldBe 401
    get("/api/v1/docs").send(locked).code.code shouldBe 401
  }

  it should "reject a wrong password" in {
    get("/api/v1/version").auth.basic("me", "nope").send(locked).code.code shouldBe 401
    get("/api/v1/version").auth.basic("you", "secret").send(locked).code.code shouldBe 401
  }

  it should "let the right credentials through" in {
    get("/api/v1/version").auth.basic("me", "secret").send(locked).code.code shouldBe 200
    get("/").auth.basic("me", "secret").send(locked).body.toOption.get should include("test webapp")
  }

  it should "keep health open" in {
    get("/api/v1/health").send(locked).code.code shouldBe 200
  }

  it should "need both user and password configured" in {
    an[IllegalArgumentException] should be thrownBy BasicAuthConfig("me", "")
  }

  it should "refuse to start without credentials when required" in {
    an[IllegalArgumentException] should be thrownBy BasicAuthConfig("", "", required = true)
  }
