package rtl

import ox.*
import rtl.config.Config

import java.time.Duration
import scala.concurrent.duration.*
import scala.util.control.NonFatal

object Main extends OxApp.Simple with Logging:
  override def run(using Ox): Unit =
    val config = Config.load()
    val deps = Dependencies.create(config)

    deps.storage.foreach { storage =>
      // before the server and ingest start, so the first /stats already has the last hour
      try
        val restored = deps.ingest.restore(storage.repository.since(deps.window.cutoff))
        logger.info(s"Restored $restored strikes from history")
      catch case NonFatal(e) => logger.error("Restoring strikes from history failed", e)
        // both catch their own errors, so they never stop the app
      fork(storage.writer.run()).discard
      fork(storage.retention.run()).discard
    }

    val binding = deps.httpApi.start()
    logger.info(s"Started on http://${binding.hostName}:${binding.port}, docs at /api/v1/docs")
    logger.info(s"Strike source: ${config.ingest.source}")

    // runs alongside the server; if it fails, the whole app stops
    fork(deps.ingest.run()).discard

    fork {
      forever {
        sleep(1.minute)
        val lastMinute = deps.window.recent(Duration.ofMinutes(1)).size
        val stored =
          deps.storage.fold("")(s => s" · stored ${s.writer.written.get}, store dropped ${s.writer.dropped.get}")
        logger.info(
          s"Last minute: $lastMinute strikes · total ${deps.counters.received.get} received, " +
            s"${deps.counters.duplicates.get} duplicates dropped$stored"
        )
      }
    }.discard
    never
