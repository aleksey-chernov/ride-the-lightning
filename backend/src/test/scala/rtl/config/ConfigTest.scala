package rtl.config

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import rtl.history.HistoryConfig

import java.nio.file.Path

import scala.concurrent.duration.*

class ConfigTest extends AnyFlatSpec with Matchers:
  "application.conf" should "load into Config" in {
    Config.load().strikes.window shouldBe 60.minutes
  }

  it should "keep history for 7 days in data/history.db by default" in {
    Config.load().history shouldBe HistoryConfig(enabled = true, Path.of("data/history.db"), 7.days, 100_000)
  }
