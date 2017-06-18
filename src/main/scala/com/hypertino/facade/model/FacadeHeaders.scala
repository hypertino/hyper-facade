package com.hypertino.facade.model

import com.hypertino.hyperbus.model.Header

object FacadeHeaders {
  val CONTENT_TYPE            = "Content-Type"
  val REMOTE_ADDRESS          = "Remote-Address"
  val ACCEPT_LANGUAGE         = "Accept-Language"
  val AUTHORIZATION           = "Authorization"

  val directHeaderMapping = Set(
    Header.CORRELATION_ID,
    Header.MESSAGE_ID,
    Header.REVISION,
    Header.CONTENT_TYPE,
    Header.REVISION
  )
}
