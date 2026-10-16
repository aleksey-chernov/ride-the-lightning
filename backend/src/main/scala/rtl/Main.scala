package rtl

import ox.*
import rtl.config.Config

object Main extends OxApp.Simple with Logging:
  override def run(using Ox): Unit =
    val config = Config.load()
    val deps = Dependencies.create(config)
    val binding = deps.httpApi.start()
    logger.info(s"Started on http://${binding.hostName}:${binding.port}, docs at /api/v1/docs")
    never
