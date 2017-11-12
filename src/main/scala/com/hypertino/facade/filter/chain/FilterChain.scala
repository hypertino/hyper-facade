/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filter.chain

import com.hypertino.facade.filter.model.{EventFilter, RequestFilter, ResponseFilter}
import com.hypertino.facade.model._
import com.hypertino.facade.utils.TaskUtils
import com.hypertino.hyperbus.model.{DynamicRequest, DynamicResponse}
import monix.eval.Task
import monix.execution.Scheduler

trait FilterChain {
  def filterRequest(requestContext: RequestContext)
                   (implicit scheduler: Scheduler): Task[RequestContext] = {

    TaskUtils.chain(requestContext, findRequestFilters(requestContext).map(f ⇒ f.apply(_)))
  }



  def filterResponse(requestContext: RequestContext, response: DynamicResponse)
                    (implicit scheduler: Scheduler): Task[DynamicResponse] = {
    TaskUtils.chain(response, findResponseFilters(requestContext, response).map(f ⇒ f.apply(requestContext, _ : DynamicResponse)))
  }

  def filterEvent(requestContext: RequestContext, event: DynamicRequest)
                 (implicit scheduler: Scheduler): Task[DynamicRequest] = {
    TaskUtils.chain(event, findEventFilters(requestContext, event).map(f ⇒ f.apply(requestContext, _ : DynamicRequest)))
  }

  def findRequestFilters(requestContext: RequestContext): Seq[RequestFilter]
  def findResponseFilters(context: RequestContext, response: DynamicResponse): Seq[ResponseFilter]
  def findEventFilters(context: RequestContext, event: DynamicRequest): Seq[EventFilter]
}

object FilterChain {
  val empty = SimpleFilterChain(Seq.empty,Seq.empty,Seq.empty)
}
