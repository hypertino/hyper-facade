package com.hypertino.facade.model

import com.hypertino.hyperbus.model.Header
import com.hypertino.inflector.naming._

object FacadeHeaders {
  final val CONTENT_TYPE = "Content-Type"
  final val REMOTE_ADDRESS = "Remote-Address"
  final val AUTHORIZATION = "Authorization"
  final val PRIVILEGE_AUTHORIZATION = "Privilege-Authorization"
  final val LOCATION = "Location"
  final val LINK = "Link"
  final val COUNT = "X-Count"

  val directHeaderMapping = Set(
    Header.METHOD,
    Header.STATUS_CODE,
    Header.CORRELATION_ID,
    Header.MESSAGE_ID,
    Header.REVISION,
    Header.CONTENT_TYPE,
    Header.REVISION
  )

  private val converter = new BaseConverter {
    protected val parser = DashCaseParser

    protected def createBuilder(): IdentifierBuilder = new HyphenCaseBuilder()
  }

  /*
  content-type -> Content-Type
  */
  def normalize(s: String): String = {
    if (s.length > 1) {
      converter.convert(s)
    }
    else {
      s
    }
  }
}
