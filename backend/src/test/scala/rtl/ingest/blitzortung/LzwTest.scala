package rtl.ingest.blitzortung

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LzwTest extends AnyFlatSpec with Matchers:
  "Lzw.decode" should "match the reference JS decoder on real frames" in {
    for frame <- Fixtures.frames do Lzw.decode(frame.raw) shouldBe frame.decoded
  }

  it should "handle empty and literal-only input" in {
    Lzw.decode("") shouldBe ""
    Lzw.decode("abc") shouldBe "abc"
  }

  it should "handle a code that refers to the entry being built (KwKwK)" in {
    // "a" then code 256 = "a" + "a"
    Lzw.decode("aĀ") shouldBe "aaa"
  }
