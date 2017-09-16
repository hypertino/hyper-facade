package com.hypertino.facade.utils

import com.hypertino.binders.value.Value
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, HRL, Headers, MessageHeaders}

object RequestUtils {
  def copyWith(request: DynamicRequest, hrl: HRL, method: Option[String] = None): DynamicRequest = {
    val newHeaders = MessageHeaders.builder
      .++=(request.headers)
      .withHRL(hrl)
      .withMethod(method.getOrElse(request.headers.method))
      .requestHeaders()

    request.copy(
      headers = newHeaders
    )
  }

  def copyWithNewBody(request: DynamicRequest, bodyValue: Value): DynamicRequest = {
    request.copy(
      body=DynamicBody(bodyValue,request.body.contentType)
    )
  }
}
