package com.hypertino.facade.model

import com.hypertino.hyperbus.model.Header

object FacadeHeaders {
  val CLIENT_CONTENT_TYPE     = "Content-Type"
  val CLIENT_REVISION         = "Hyperbus-Revision"
  val CLIENT_MESSAGE_ID       = "Hyperbus-Message-Id"
  val CLIENT_CORRELATION_ID   = "Hyperbus-Correlation-Id"

  val CLIENT_IP               = "X-Forwarded-For"
  val CLIENT_LANGUAGE         = "Accept-Language"
  val AUTHORIZATION           = "Authorization"

  val clientHeaderMapping = Seq(
    CLIENT_CONTENT_TYPE → Header.CONTENT_TYPE,
    CLIENT_CORRELATION_ID → Header.CORRELATION_ID,
    CLIENT_MESSAGE_ID → Header.MESSAGE_ID,
    CLIENT_REVISION → Header.REVISION
  )
}
