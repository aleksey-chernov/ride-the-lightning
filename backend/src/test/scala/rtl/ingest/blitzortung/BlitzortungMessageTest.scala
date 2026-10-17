package rtl.ingest.blitzortung

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant

class BlitzortungMessageTest extends AnyFlatSpec with Matchers with EitherValues:
  "parse" should "read every real frame" in {
    for frame <- Fixtures.frames do
      val strike = BlitzortungMessage.parse(frame.decoded).value.toStrike
      strike.stations should be > 0
      strike.lat should (be >= -90.0 and be <= 90.0)
  }

  it should "keep nanosecond precision, apply corrections and ignore unknown fields" in {
    val json = """{"time":1792258216966221307,"lat":10.5,"lon":20.0,"latc":0.25,"newField":"x",
                 |"sig":[{"sta":1,"lat":1,"lon":2,"alt":3}],"region":7,"delay":4.2,"mds":8000}""".stripMargin
    val strike = BlitzortungMessage.parse(json).value.toStrike
    strike.time shouldBe Instant.ofEpochSecond(1792258216L, 966221307L)
    strike.lat shouldBe 10.75
    strike.stations shouldBe 1
    strike.region shouldBe 7
  }

  it should "fail when a required field is missing" in {
    BlitzortungMessage.parse("""{"time":1,"lon":2}""").isLeft shouldBe true
  }
