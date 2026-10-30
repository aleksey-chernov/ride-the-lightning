package rtl.history

import com.github.plokhotnyuk.jsoniter_scala.macros.ConfiguredJsonValueCodec
import rtl.http.ResponseContract.given
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.description

import java.time.Instant

case class HistoryRangeResponse(
    @description("False when this server keeps no history") enabled: Boolean,
    @description("Oldest stored strike; absent when nothing is stored") oldest: Option[Instant],
    @description("Newest stored strike; absent when nothing is stored") newest: Option[Instant],
    @description("How long strikes are kept, in days") keepDays: Int
) derives ConfiguredJsonValueCodec,
      Schema

case class PlaybackStrikeResponse(
    @description("Strike time, epoch milliseconds") timeMs: Long,
    lat: Double,
    lon: Double
) derives ConfiguredJsonValueCodec,
      Schema

object PlaybackStrikeResponse:
  def from(row: PlaybackRow): PlaybackStrikeResponse =
    PlaybackStrikeResponse(row.time.toEpochMilli, row.lat, row.lon)

case class HistoryStrikesResponse(
    from: Instant,
    to: Instant,
    @description("1 = every stored strike in the range; n = only every n-th, to stay under the server's limit")
    step: Int,
    @description("Oldest first") strikes: List[PlaybackStrikeResponse]
) derives ConfiguredJsonValueCodec,
      Schema
