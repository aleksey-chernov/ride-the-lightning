package rtl.config

import pureconfig.{ConfigReader, ConfigSource}
import rtl.http.HttpConfig

case class Config(http: HttpConfig) derives ConfigReader

object Config:
  def load(): Config = ConfigSource.default.loadOrThrow[Config]
