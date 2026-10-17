package rtl.config

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

class ConfigTest extends AnyFlatSpec with Matchers:
  "application.conf" should "load into Config" in {
    Config.load().strikes.window shouldBe 60.minutes
  }
