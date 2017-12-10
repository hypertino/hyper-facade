/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.chain.after_reply

import com.hypertino.binders.value.Obj
import com.hypertino.facade.apiref.idempotency.{IdempotentResponsePut, ResponseWrapper}
import com.hypertino.facade.filter.model.ResponseFilter
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.metrics.MetricKeys
import com.hypertino.facade.model.RequestContext
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{DynamicResponse, MessagingContext}
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler

import scala.util.{Failure, Success}

// todo: cover with test
class IdempotencyResponseFilter(hyperbus: Hyperbus,
                                protected val expressionEvaluator: ExpressionEvaluator,
                                protected implicit val scheduler: Scheduler) extends ResponseFilter with StrictLogging {

  val timer = Some(MetricKeys.specificFilter("IdempotencyResponseFilter"))

  override def apply(requestContext: RequestContext, response: DynamicResponse)
                    (implicit scheduler: Scheduler): Task[DynamicResponse] = {

    implicit val mcx: MessagingContext = requestContext
    requestContext.contextStorage.dynamic.idempotent_request match {
      case o: Obj ⇒ saveIdempotentResponse(o.dynamic.uri.toString, o.dynamic.key.toString, response)
      case _ ⇒ Task.now(response)
    }
  }

  private def saveIdempotentResponse(uri: String, key: String, response: DynamicResponse)
                                    (implicit mcx: MessagingContext): Task[DynamicResponse] = {
    val responseWrapper = ResponseWrapper(Obj(response.headers.underlying), response.body.content)
    hyperbus
      .ask(IdempotentResponsePut(uri,key,responseWrapper))
      .onErrorRestart(3)
      .materialize
      .map {
        case Success(_) ⇒
          response

        case Failure(e) ⇒
          logger.error(s"Failed to save idempotent response for $uri/$key, this may lead to locked resource", e)
          response
      }
  }
}