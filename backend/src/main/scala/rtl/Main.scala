package rtl

import ox.*
import rtl.config.Config

import java.time.Duration
import scala.concurrent.duration.*

object Main extends OxApp.Simple with Logging:
  override def run(using Ox): Unit =
    val config = Config.load()
    val deps = Dependencies.create(config)
    val binding = deps.httpApi.start()
    logger.info(s"Started on http://${binding.hostName}:${binding.port}, docs at /api/v1/docs")
    logger.info(s"Strike source: ${config.ingest.source}")

    // runs alongside the server; if it fails, the whole app stops
    fork(deps.ingest.run()).discard

    fork {
      forever {
        sleep(1.minute)
        val lastMinute = deps.window.recent(Duration.ofMinutes(1)).size
        logger.info(
          s"Last minute: $lastMinute strikes · total ${deps.counters.received.get} received, " +
            s"${deps.counters.duplicates.get} duplicates dropped"
        )
      }
    }.discard
    never
