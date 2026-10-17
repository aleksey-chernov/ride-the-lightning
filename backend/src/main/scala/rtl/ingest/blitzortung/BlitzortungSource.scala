package rtl.ingest.blitzortung

import ox.*
import ox.flow.{Flow, FlowEmit}
import ox.resilience.{retry, RetryConfig}
import ox.scheduling.{Jitter, Schedule}
import rtl.Logging
import rtl.ingest.{BlitzortungConfig, StrikeSource}
import rtl.strikes.Strike
import sttp.client4.*
import sttp.client4.ws.sync.*

import scala.concurrent.duration.*
import scala.util.Random

/** Live feed: one websocket connection, reconnects with exponential backoff + jitter, rotating servers. */
class BlitzortungSource(config: BlitzortungConfig, backend: WebSocketSyncBackend) extends StrikeSource with Logging:
  private val reconnectSchedule = Schedule.exponentialBackoff(1.second).maxInterval(1.minute).jitter(Jitter.Equal)

  def strikes: Flow[Strike] = messages.mapConcat { json =>
    BlitzortungMessage.parse(json) match
      case Right(msg) => List(msg.toStrike)
      case Left(e)    => logger.warn(s"Skipping unparsable message: ${e.getMessage}"); Nil
  }

  /** Decoded JSON messages, as received. */
  def messages: Flow[String] = Flow.usingEmit { emit =>
    var server = ""
    val retryConfig = RetryConfig[Throwable, Unit](
      reconnectSchedule,
      afterAttempt = (attempt, result) => result.left.foreach(e => logger.warn(s"Connection #$attempt lost: $e"))
    )
    retry(retryConfig) {
      server = nextServer(server)
      session(server, emit)
    }
  }

  private def session(server: String, emit: FlowEmit[String]): Unit =
    logger.info(s"Connecting to wss://$server")
    val response = basicRequest
      .get(uri"wss://$server")
      .response(asWebSocket { ws =>
        ws.sendText("""{"a":111}""")
        forever(emit(Lzw.decode(ws.receiveText())))
      })
      .send(backend)
    // a session only ends by an exception; a returned response means the upgrade failed
    throw IllegalStateException(s"Websocket upgrade failed: ${response.code} ${response.body}")

  private def nextServer(previous: String): String =
    val others = config.servers.filterNot(_ == previous)
    val candidates = if others.nonEmpty then others else config.servers
    candidates(Random.nextInt(candidates.size))
