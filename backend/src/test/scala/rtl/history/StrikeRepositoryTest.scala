package rtl.history

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import rtl.TestStrikes.strike
import rtl.{TestClock, TestDb}

import java.time.{Duration, Instant}

class StrikeRepositoryTest extends AnyFlatSpec with Matchers with TestDb:
  private val repository = StrikeRepository(db)
  private val t0 = Instant.parse("2026-10-17T12:00:00.123456Z")

  private def at(seconds: Int) = strike(t0.plusSeconds(seconds), lat = seconds.toDouble)

  "insert and since" should "round-trip a strike exactly" in {
    val s = strike(t0, lat = 41.683882, lon = 2.271206, region = 9, stations = 12, delaySeconds = 5.2, mds = 10710)
    repository.insert(Seq(s))
    repository.since(t0) shouldBe Vector(s)
  }

  "since" should "return strikes at or after the bound, oldest first" in {
    repository.insert(Seq(at(3), at(1), at(2)))
    repository.since(t0.plusSeconds(2)).map(_.lat) shouldBe Vector(2.0, 3.0)
  }

  "page" should "return the half-open range [from, to) with step 1 under the limit" in {
    repository.insert((0 until 5).map(at))
    val page = repository.page(t0.plusSeconds(1), t0.plusSeconds(4), max = 10)
    page.step shouldBe 1
    page.rows.map(_.lat) shouldBe Vector(1.0, 2.0, 3.0)
    page.rows.head.time shouldBe t0.plusSeconds(1)
  }

  it should "keep every step-th strike above the limit" in {
    repository.insert((1 to 10).map(at))
    val page = repository.page(t0, t0.plusSeconds(60), max = 3)
    page.step shouldBe 4 // ceil(10 / 3)
    page.rows.map(_.lat) shouldBe Vector(4.0, 8.0)
  }

  "range" should "be empty without strikes and give oldest and newest otherwise" in {
    repository.range() shouldBe None
    repository.insert(Seq(at(5), at(1), at(3)))
    repository.range() shouldBe Some((t0.plusSeconds(1), t0.plusSeconds(5)))
  }

  "deleteOlderThan" should "delete only strikes before the cutoff" in {
    repository.insert(Seq(at(1), at(2), at(3)))
    repository.deleteOlderThan(t0.plusSeconds(2)) shouldBe 1
    repository.since(t0).map(_.lat) shouldBe Vector(2.0, 3.0)
  }

  "Retention.cleanUp" should "delete strikes older than keep by the app clock" in {
    val clock = TestClock(t0.plus(Duration.ofDays(7)).plusSeconds(2))
    repository.insert(Seq(at(1), at(2), at(3)))
    Retention(repository, Duration.ofDays(7), clock).cleanUp() shouldBe 1
    repository.since(t0).map(_.lat) shouldBe Vector(2.0, 3.0)
  }
