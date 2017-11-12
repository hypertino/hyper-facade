/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

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
