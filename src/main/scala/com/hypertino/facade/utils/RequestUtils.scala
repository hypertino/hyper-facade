/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.utils

import com.hypertino.binders.value.Value
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, HRL, MessageHeaders}

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
      body = DynamicBody(bodyValue, request.body.contentType)
    )
  }
}
