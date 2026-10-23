package rtl.strikes

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.github.plokhotnyuk.jsoniter_scala.macros.ConfiguredJsonValueCodec
import ox.flow.Flow
import rtl.http.Http.baseEndpoint
import rtl.http.ResponseContract.given
import sttp.model.sse.ServerSentEvent
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.json.jsoniter.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.sync.{OxStreams, serverSentEventsBody}

import java.time.{Duration, Instant}
import scala.concurrent.duration.*

class StrikesApi(window: StrikeWindow, broadcaster: StrikeBroadcaster):
  import StrikesApi.*

  val restEndpoints: List[ServerEndpoint[Any, Identity]] = List(
    recentEndpoint.handleSuccess { (minutes, limit) =>
      val strikes = window.recent(Duration.ofMinutes(minutes)).sortBy(_.time).reverse.take(limit)
      RecentStrikesResponse(strikes.map(StrikeResponse.from).toList)
    }
  )

  val streamEndpoints: List[ServerEndpoint[OxStreams, Identity]] = List(
    streamEndpoint.handleSuccess { _ =>
      val strikes = broadcaster.flow.map(s => strikeEvent(StrikeResponse.from(s)))
      val heartbeats =
        Flow.tick(HeartbeatInterval).map(_ => ServerSentEvent(data = Some(""), eventType = Some("heartbeat")))
      strikes.merge(heartbeats)
    }
  )

  val endpoints: List[ServerEndpoint[OxStreams, Identity]] = restEndpoints ++ streamEndpoints

object StrikesApi:
  val HeartbeatInterval: FiniteDuration = 15.seconds

  def strikeEvent(strike: StrikeResponse): ServerSentEvent =
    ServerSentEvent(data = Some(writeToString(strike)), eventType = Some("strike"))

  val recentEndpoint = baseEndpoint.get
    .in("strikes" / "recent")
    .in(query[Int]("minutes").default(15).validate(Validator.inRange(1, 60)))
    .in(query[Int]("limit").default(5000).validate(Validator.inRange(1, 50000)))
    .out(jsonBody[RecentStrikesResponse])
    .description("Deduplicated strikes, newest first")
    .tag("strikes")

  val streamEndpoint = baseEndpoint.get
    .in("strikes" / "stream")
    .out(serverSentEventsBody)
    .description("Server-Sent Events: `strike` (StrikeResponse JSON) for each new strike, `heartbeat` every 15 s")
    .tag("stream") // separate tag: generated HTTP clients skip it, browsers use EventSource

case class StrikeResponse(
    time: Instant,
    lat: Double,
    lon: Double,
    region: Int,
    stations: Int,
    delaySeconds: Double,
    mds: Int
) derives ConfiguredJsonValueCodec,
      Schema

object StrikeResponse:
  def from(s: Strike): StrikeResponse =
    StrikeResponse(s.time, s.lat, s.lon, s.region, s.stations, s.delaySeconds, s.mds)

case class RecentStrikesResponse(strikes: List[StrikeResponse]) derives ConfiguredJsonValueCodec, Schema
