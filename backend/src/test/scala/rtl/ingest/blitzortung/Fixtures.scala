package rtl.ingest.blitzortung

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.{ConfiguredJsonValueCodec, JsonCodecMaker}

import scala.io.Source

case class Frame(raw: String, decoded: String) derives ConfiguredJsonValueCodec

object Fixtures:
  private given JsonValueCodec[List[Frame]] = JsonCodecMaker.make

  // 3 real frames captured from ws1.blitzortung.org, decoded by the site's JS algorithm
  lazy val frames: List[Frame] =
    val json = Source.fromResource("blitzortung/frames.json").mkString
    readFromString[List[Frame]](json)
