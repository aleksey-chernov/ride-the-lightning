package rtl.http

import com.github.plokhotnyuk.jsoniter_scala.macros.ConfiguredJsonValueCodec
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.jsoniter.*

object Http:
  val jsonErrorOutput: EndpointOutput[Error_OUT] = jsonBody[Error_OUT]

  // Shared base for all endpoints: errors are JSON with a status code.
  val baseEndpoint: PublicEndpoint[Unit, (StatusCode, Error_OUT), Unit, Any] =
    endpoint.errorOut(statusCode.and(jsonErrorOutput))

case class Error_OUT(error: String) derives ConfiguredJsonValueCodec, Schema
