package com.hypertino.facade.utils

import com.hypertino.hyperbus.model.{DynamicRequest, HRL, Headers}

object RequestUtils {
  def copyWithNewHRL(request: DynamicRequest, hrl: HRL): DynamicRequest = {
    val newHeaders = Headers.builder
      .++=(request.headers)
      .withHRL(hrl)
      .requestHeaders()

    request.copy(
      headers = newHeaders
    )
  }
}
