package rtl.http

import com.github.plokhotnyuk.jsoniter_scala.macros.CodecMakerConfig
import sttp.tapir.Schema

/** Response contract: collections are always present (`[]`, never omitted) in JSON and marked required in OpenAPI.
  *
  * Import `rtl.http.ResponseContract.given` where response types derive their codecs and schemas.
  */
object ResponseContract:
  inline given CodecMakerConfig = CodecMakerConfig.withTransientEmpty(false)

  given [T](using item: Schema[T]): Schema[List[T]] = item.asIterable[List].copy(isOptional = false)
