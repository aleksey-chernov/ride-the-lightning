package rtl.config

import pureconfig.{ConfigReader, ConfigSource}
import rtl.geo.GeoConfig
import rtl.http.HttpConfig
import rtl.ingest.IngestConfig
import rtl.strikes.StrikesConfig

case class Config(http: HttpConfig, ingest: IngestConfig, strikes: StrikesConfig, geo: GeoConfig) derives ConfigReader

object Config:
  def load(): Config = ConfigSource.default.loadOrThrow[Config]
