package rtl.http

import com.github.plokhotnyuk.jsoniter_scala.macros.ConfiguredJsonValueCodec
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.jsoniter.*

object Http:
  val jsonErrorOutput: EndpointOutput[ErrorResponse] = jsonBody[ErrorResponse]

  // Shared base for all endpoints: errors are JSON with a status code.
  val baseEndpoint: PublicEndpoint[Unit, (StatusCode, ErrorResponse), Unit, Any] =
    endpoint.errorOut(statusCode.and(jsonErrorOutput))

case class ErrorResponse(error: String) derives ConfiguredJsonValueCodec, Schema
