/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.model

import com.hypertino.binders.value.Obj
import com.hypertino.hyperbus.model.{DynamicRequest, Headers, MessagingContext, RequestHeaders}

case class RequestContext(request: DynamicRequest,
                          stages: Seq[RequestHeaders],
                          contextStorage: Obj,
                          ramlEntryHeaders: Option[RequestHeaders] = None) extends MessagingContext {

  lazy val httpHeaders: RequestHeaders = RequestHeaders(
    Headers(stages.reverse.head.toSeq.map(kv ⇒ FacadeHeaders.normalize(kv._1) → kv._2): _*)
  )
  lazy val remoteAddress: String = httpHeaders(FacadeHeaders.REMOTE_ADDRESS).toString

  def withNextStage(newRequest: DynamicRequest, ramlEntryHeaders: Option[RequestHeaders] = None): RequestContext = copy(
    stages = Seq(request.headers) ++ stages,
    request = newRequest,
    ramlEntryHeaders = this.ramlEntryHeaders.orElse(ramlEntryHeaders)
  )

  override def correlationId: String = request.correlationId

  override def parentId: Option[String] = request.parentId
}

object RequestContext {

  def apply(request: DynamicRequest): RequestContext = RequestContext(
    request,
    stages = Seq.empty,
    contextStorage = Obj.empty
  ).withNextStage(request)
}
