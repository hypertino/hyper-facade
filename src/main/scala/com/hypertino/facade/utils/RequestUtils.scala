package com.hypertino.facade.utils

import com.hypertino.hyperbus.model.{DynamicRequest, HRL, Headers}

object RequestUtils {
  def copyWithNewHRL(request: DynamicRequest, hrl: HRL): DynamicRequest = {
    val rewrittenUri = HrlTransformer.rewrite(request.headers.hrl, hrl)
    val newHeaders = Headers.builder
      .++=(request.headers)
      .withHRL(rewrittenUri)
      .requestHeaders()

    request.copy(
      headers = newHeaders
    )
  }
}
