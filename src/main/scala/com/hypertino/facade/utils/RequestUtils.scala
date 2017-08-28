package com.hypertino.facade.utils

import com.hypertino.hyperbus.model.{DynamicRequest, HRL, Headers}

object RequestUtils {
  def copyWith(request: DynamicRequest, hrl: HRL, method: Option[String] = None): DynamicRequest = {
    val newHeaders = Headers.builder
      .++=(request.headers)
      .withHRL(hrl)
      .withMethod(method.getOrElse(request.headers.method))
      .requestHeaders()

    request.copy(
      headers = newHeaders
    )
  }
}
