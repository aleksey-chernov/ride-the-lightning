package rtl.geo

import pureconfig.ConfigReader

import java.nio.file.Path

case class GeoConfig(cacheDir: Path) derives ConfigReader
