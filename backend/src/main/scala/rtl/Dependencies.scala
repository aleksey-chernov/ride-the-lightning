package rtl

import rtl.admin.AdminApi
import rtl.config.Config
import rtl.http.HttpApi

// Manual wiring: build every component here, passing dependencies via constructors.
case class Dependencies(httpApi: HttpApi)

object Dependencies:
  def create(config: Config): Dependencies =
    val adminApi = AdminApi()
    Dependencies(HttpApi(adminApi.endpoints, config.http))
