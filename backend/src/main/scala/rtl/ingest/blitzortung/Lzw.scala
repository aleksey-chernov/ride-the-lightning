package rtl.ingest.blitzortung

import scala.collection.mutable.ArrayBuffer

/** Decoder for Blitzortung's LZW-compressed websocket frames. */
object Lzw:
  def decode(input: String): String =
    if input.isEmpty then ""
    else
      val dict = ArrayBuffer.empty[String] // index = code - 256
      var prev = input.substring(0, 1)
      val out = StringBuilder(prev)
      for i <- 1 until input.length do
        val code = input.charAt(i).toInt
        val phrase =
          if code < 256 then input.charAt(i).toString
          else if code - 256 < dict.size then dict(code - 256)
          else prev + prev.charAt(0) // code not in dict yet (KwKwK case)
        out ++= phrase
        dict += prev + phrase.charAt(0)
        prev = phrase
      out.toString
