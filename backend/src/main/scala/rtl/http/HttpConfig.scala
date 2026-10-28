package rtl.http

import pureconfig.ConfigReader
import sttp.tapir.model.UsernamePassword

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

case class HttpConfig(host: String, port: Int, auth: BasicAuthConfig = BasicAuthConfig.Off) derives ConfigReader

/** HTTP basic auth for the whole app; off when both fields are empty. `required` refuses to start without it. */
case class BasicAuthConfig(user: String, password: String, required: Boolean = false) derives ConfigReader:
  require(user.isEmpty == password.isEmpty, "set both BASIC_AUTH_USER and BASIC_AUTH_PASSWORD, or neither")
  require(enabled || !required, "REQUIRE_BASIC_AUTH is set, but BASIC_AUTH_USER/BASIC_AUTH_PASSWORD are not")

  def enabled: Boolean = password.nonEmpty

  def accepts(credentials: UsernamePassword): Boolean =
    // constant-time comparison, so response timing doesn't leak how much of the password matched
    def same(a: String, b: String) = MessageDigest.isEqual(a.getBytes(UTF_8), b.getBytes(UTF_8))
    same(credentials.username, user) & same(credentials.password.getOrElse(""), password)

  override def toString: String = s"BasicAuthConfig($user, ${if enabled then "***" else ""}, $required)"

object BasicAuthConfig:
  val Off: BasicAuthConfig = BasicAuthConfig("", "")
