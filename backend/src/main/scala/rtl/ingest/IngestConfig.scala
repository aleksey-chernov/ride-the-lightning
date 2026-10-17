package rtl.ingest

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

enum SourceKind:
  case Blitzortung, Replay

object SourceKind:
  given ConfigReader[SourceKind] = ConfigReader[String].emap { s =>
    SourceKind.values.find(_.toString.equalsIgnoreCase(s)).toRight(CannotConvert(s, "SourceKind", "unknown source"))
  }

case class BlitzortungConfig(servers: List[String]) derives ConfigReader

case class ReplayConfig(file: String, speed: Double) derives ConfigReader

case class IngestConfig(source: SourceKind, blitzortung: BlitzortungConfig, replay: ReplayConfig) derives ConfigReader
