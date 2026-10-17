package rtl.config

import pureconfig.{ConfigReader, ConfigSource}
import rtl.http.HttpConfig
import rtl.ingest.IngestConfig
import rtl.strikes.StrikesConfig

case class Config(http: HttpConfig, ingest: IngestConfig, strikes: StrikesConfig) derives ConfigReader

object Config:
  def load(): Config = ConfigSource.default.loadOrThrow[Config]
