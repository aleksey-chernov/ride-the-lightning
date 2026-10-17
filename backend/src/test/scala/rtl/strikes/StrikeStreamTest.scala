package rtl.strikes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ox.*
import rtl.TestClock
import rtl.TestStrikes.strike
import rtl.http.{HttpApi, HttpConfig}
import sttp.client4.*

import java.io.{BufferedReader, InputStream, InputStreamReader}
import java.time.Duration
import scala.concurrent.duration.*

/** SSE needs a real server: the stub interpreter used elsewhere doesn't support streaming. */
class StrikeStreamTest extends AnyFlatSpec with Matchers:
  /** Reads SSE events (blank-line separated) until the first `strike` event; returns its lines. */
  private def readFirstStrikeEvent(in: InputStream): List[String] =
    val reader = BufferedReader(InputStreamReader(in))
    def nextEvent(): List[String] =
      Iterator.continually(reader.readLine()).takeWhile(l => l != null && l.nonEmpty).toList
    Iterator.continually(nextEvent()).find(_.contains("event: strike")).getOrElse(Nil)

  "GET /strikes/stream" should "push each new strike as an SSE event" in supervised {
    val clock = TestClock()
    val broadcaster = StrikeBroadcaster()
    val api = StrikesApi(StrikeWindow(Duration.ofMinutes(60), clock), broadcaster)
    val binding = HttpApi(api.endpoints, HttpConfig("localhost", 0)).start()
    val backend = useCloseableInScope(DefaultSyncBackend())

    val event = fork {
      basicRequest
        .get(uri"http://localhost:${binding.port}/api/v1/strikes/stream")
        .response(asInputStreamAlways(readFirstStrikeEvent))
        .send(backend)
        .body
    }
    while broadcaster.subscriberCount == 0 do sleep(10.millis)
    broadcaster.publish(strike(clock.now, lat = 12.5))

    val lines = timeout(5.seconds)(event.join())
    lines should contain("event: strike")
    lines.find(_.startsWith("data:")).get should include("\"lat\":12.5")
  }
