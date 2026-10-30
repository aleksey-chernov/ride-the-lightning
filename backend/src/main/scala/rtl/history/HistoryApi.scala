package rtl.history

import rtl.http.ErrorResponse
import rtl.http.Http.baseEndpoint
import sttp.model.StatusCode
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.json.jsoniter.*
import sttp.tapir.server.ServerEndpoint

import java.time.{Duration, Instant}
import scala.jdk.DurationConverters.*

/** Stored strikes for playback; `repository` is None when this server keeps no history. */
class HistoryApi(repository: Option[StrikeRepository], config: HistoryConfig):
  import HistoryApi.*

  private val keep: Duration = config.keep.toJava

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    rangeEndpoint.handleSuccess { _ =>
      val range = repository.flatMap(_.range())
      HistoryRangeResponse(repository.isDefined, range.map(_._1), range.map(_._2), keep.toDays.toInt)
    },
    strikesEndpoint.handle { (from, to) =>
      repository match
        case None                         => Left(error(StatusCode.ServiceUnavailable, "History is off on this server"))
        case Some(_) if !to.isAfter(from) => Left(error(StatusCode.BadRequest, "`to` must be after `from`"))
        case Some(_) if outOfRange(from) || outOfRange(to) =>
          Left(error(StatusCode.BadRequest, "Instants must be between 1970 and 9999"))
        case Some(_) if tooLong(from, to) =>
          Left(error(StatusCode.BadRequest, s"The range may span at most ${keep.toDays} days"))
        case Some(r) =>
          val page = r.page(from, to, config.maxStrikes)
          Right(HistoryStrikesResponse(from, to, page.step, page.rows.map(PlaybackStrikeResponse.from).toList))
    }
  )

  // also keeps the µs encoding from overflowing a Long
  private def outOfRange(t: Instant): Boolean = t.isBefore(Instant.EPOCH) || t.isAfter(MaxInstant)

  private def tooLong(from: Instant, to: Instant): Boolean = Duration.between(from, to).compareTo(keep) > 0

  private def error(code: StatusCode, message: String) = (code, ErrorResponse(message))

object HistoryApi:
  private val MaxInstant = Instant.parse("9999-12-31T23:59:59Z")

  val rangeEndpoint = baseEndpoint.get
    .in("history" / "range")
    .out(jsonBody[HistoryRangeResponse])
    .description("Whether history is kept, and the oldest and newest stored strike")
    .tag("history")

  val strikesEndpoint = baseEndpoint.get
    .in("history" / "strikes")
    .in(query[Instant]("from").description("Start, inclusive (ISO-8601, e.g. 2026-10-29T12:00:00Z)"))
    .in(query[Instant]("to").description("End, exclusive (ISO-8601)"))
    .out(jsonBody[HistoryStrikesResponse])
    .description(
      "Stored strikes in [from, to), oldest first; long ranges return only every `step`-th strike. " +
        "503 when this server keeps no history"
    )
    .tag("history")
