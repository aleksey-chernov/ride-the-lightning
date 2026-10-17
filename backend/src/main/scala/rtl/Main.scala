package rtl

import ox.*
import rtl.config.Config

object Main extends OxApp.Simple with Logging:
  override def run(using Ox): Unit =
    val config = Config.load()
    val deps = Dependencies.create(config)
    val binding = deps.httpApi.start()
    logger.info(s"Started on http://${binding.hostName}:${binding.port}, docs at /api/v1/docs")
    logger.info(s"Strike source: ${config.ingest.source}")

    // runs alongside the server; if it fails, the whole app stops
    fork {
      deps.strikeSource.strikes.runForeach { s =>
        logger.info(
          f"⚡ ${s.lat}%7.3f ${s.lon}%8.3f  region=${s.region} stations=${s.stations} delay=${s.delaySeconds}%.1fs"
        )
      }
    }.discard
    never
