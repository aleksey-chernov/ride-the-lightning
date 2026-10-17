package rtl.ingest

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import rtl.ingest.blitzortung.Fixtures

import java.nio.file.Files
import java.time.{Duration, Instant}

class ReplaySourceTest extends AnyFlatSpec with Matchers:
  "ReplaySource" should "replay recorded strikes in a loop with times shifted to now" in {
    val file = Files.createTempFile("replay", ".jsonl")
    Files.write(file, Fixtures.frames.map(_.decoded).mkString("\n").getBytes)

    val before = Instant.now()
    val strikes = ReplaySource(ReplayConfig(file.toString, speed = 1_000_000)).strikes.take(6).runToList()

    strikes should have size 6 // 3 recorded strikes, looped twice
    strikes.map(_.lat).take(3) shouldBe strikes.map(_.lat).drop(3)
    all(strikes.map(s => Duration.between(before, s.time).abs.toSeconds)) should be < 5L
  }
