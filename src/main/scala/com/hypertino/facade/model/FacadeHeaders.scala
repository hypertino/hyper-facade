package com.hypertino.facade.model

import com.hypertino.hyperbus.model.Header

object FacadeHeaders {
  val CONTENT_TYPE            = "content-type"
  val REMOTE_ADDRESS          = "remote-address"
  val AUTHORIZATION           = "authorization"
  val PRIVILEGE_AUTHORIZATION = "privilege-authorization"

  val directHeaderMapping = Set(
    Header.METHOD,
    Header.STATUS_CODE,
    Header.CORRELATION_ID,
    Header.MESSAGE_ID,
    Header.REVISION,
    Header.CONTENT_TYPE,
    Header.REVISION
  )
}
