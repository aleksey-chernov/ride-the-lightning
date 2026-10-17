package rtl.strikes

import pureconfig.ConfigReader

import scala.concurrent.duration.FiniteDuration

case class StrikesConfig(window: FiniteDuration) derives ConfigReader // HOCON style: "60 minutes"
