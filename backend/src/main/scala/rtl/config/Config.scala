package rtl.config

import pureconfig.{ConfigReader, ConfigSource}
import rtl.http.HttpConfig
import rtl.ingest.IngestConfig

case class Config(http: HttpConfig, ingest: IngestConfig) derives ConfigReader

object Config:
  def load(): Config = ConfigSource.default.loadOrThrow[Config]
