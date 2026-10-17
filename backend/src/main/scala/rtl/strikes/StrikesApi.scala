package rtl.strikes

import com.github.plokhotnyuk.jsoniter_scala.macros.ConfiguredJsonValueCodec
import rtl.http.Http.baseEndpoint
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.json.jsoniter.*
import sttp.tapir.server.ServerEndpoint

import java.time.{Duration, Instant}

class StrikesApi(window: StrikeWindow):
  import StrikesApi.*

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    recentEndpoint.handleSuccess { (minutes, limit) =>
      val strikes = window.recent(Duration.ofMinutes(minutes)).sortBy(_.time).reverse.take(limit)
      RecentStrikesResponse(strikes.map(StrikeResponse.from).toList)
    }
  )

object StrikesApi:
  val recentEndpoint = baseEndpoint.get
    .in("strikes" / "recent")
    .in(query[Int]("minutes").default(15).validate(Validator.inRange(1, 60)))
    .in(query[Int]("limit").default(5000).validate(Validator.inRange(1, 50000)))
    .out(jsonBody[RecentStrikesResponse])
    .description("Deduplicated strikes, newest first")
    .tag("strikes")

case class StrikeResponse(
    time: Instant,
    lat: Double,
    lon: Double,
    region: Int,
    stations: Int,
    delaySeconds: Double,
    mds: Int
) derives Schema

object StrikeResponse:
  def from(s: Strike): StrikeResponse =
    StrikeResponse(s.time, s.lat, s.lon, s.region, s.stations, s.delaySeconds, s.mds)

case class RecentStrikesResponse(strikes: List[StrikeResponse]) derives ConfiguredJsonValueCodec, Schema
