package rtl.history

import pureconfig.ConfigReader

import java.nio.file.Path
import scala.concurrent.duration.FiniteDuration

case class HistoryConfig(enabled: Boolean, file: Path, keep: FiniteDuration, maxStrikes: Int) derives ConfigReader
