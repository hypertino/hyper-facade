/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.annotated

import com.hypertino.binders.value.Lst
import com.hypertino.facade.filter.model.ResponseFilter
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.metrics.MetricKeys
import com.hypertino.facade.model._
import com.hypertino.hyperbus.model.{DynamicBody, DynamicResponse, ErrorBody, InternalServerError, NotFound, StandardResponse}
import monix.eval.Task
import monix.execution.Scheduler

class ExtractItemResponseFilter(protected val expressionEvaluator: ExpressionEvaluator) extends ResponseFilter {

  val timer = Some(MetricKeys.specificFilter("ExtractItemResponseFilter"))

  override def apply(requestContext: RequestContext, response: DynamicResponse)
                    (implicit scheduler: Scheduler): Task[DynamicResponse] = {
    implicit val mcx = requestContext.request
    response.body.content match {
      case Lst(items) ⇒
        if (items.isEmpty) Task.raiseError {
          NotFound(ErrorBody(ErrorCode.COLLECTION_IS_EMPTY, Some(s"Resource ${requestContext.request.headers.hrl} is an empty collection")))
        }
        else {
          if (items.size > 1) Task.raiseError {
            InternalServerError(ErrorBody(ErrorCode.COLLECTION_HAVE_MORE_THAN_1_ITEMS, Some(s"Resource ${requestContext.request.headers.hrl} have ${items.size} items")))
          }
          else Task.now {
            StandardResponse(DynamicBody(items.head), response.headers).asInstanceOf[DynamicResponse]
          }
        }

      case _ ⇒
        Task.raiseError {
          InternalServerError(ErrorBody(ErrorCode.RESOURCE_IS_NOT_COLLECTION))
        }
    }
  }
}


