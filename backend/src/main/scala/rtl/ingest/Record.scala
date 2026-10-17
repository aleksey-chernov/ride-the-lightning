package rtl.ingest

import ox.*
import rtl.Logging
import rtl.config.Config
import rtl.ingest.blitzortung.BlitzortungSource
import sttp.client4.DefaultSyncBackend

import java.nio.file.{Files, Path}
import scala.concurrent.duration.*

/** Records live messages to the replay file: `sbt "backend/runMain rtl.ingest.Record 600"` (seconds). */
object Record extends OxApp with Logging:
  def run(args: Vector[String])(using Ox): ExitCode =
    val seconds = args.headOption.map(_.toInt).getOrElse(600)
    val config = Config.load().ingest
    val backend = useCloseableInScope(DefaultSyncBackend())
    val file = Path.of(config.replay.file)
    Files.createDirectories(file.toAbsolutePath.getParent)
    val writer = useCloseableInScope(Files.newBufferedWriter(file))
    var count = 0
    logger.info(s"Recording for $seconds s to $file")
    timeoutOption(seconds.seconds) {
      BlitzortungSource(config.blitzortung, backend).messages.runForeach { json =>
        writer.write(json)
        writer.newLine()
        count += 1
        if count % 100 == 0 then logger.info(s"Recorded $count messages")
      }
    }.discard
    logger.info(s"Done: $count messages in $file")
    ExitCode.Success
