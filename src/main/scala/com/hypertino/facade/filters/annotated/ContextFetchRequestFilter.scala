/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.annotated

import com.hypertino.binders.value.{Lst, Obj, Value}
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, ExpressionEvaluatorContext}
import com.hypertino.facade.model._
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.HRL
import monix.eval.Task
import monix.execution.Scheduler

import scala.util.control.NonFatal

class ContextFetchRequestFilter(protected val annotation: ContextFetchAnnotation,
                                protected val hyperbus: Hyperbus,
                                protected val expressionEvaluator: ExpressionEvaluator,
                                protected implicit val scheduler: Scheduler) extends RequestFilter with FetchFilterBase {

  override def apply(requestContext: RequestContext)
                    (implicit scheduler: Scheduler): Task[RequestContext] = {

    fetchAndReturn(requestContext).map {
      case Some(v) ⇒
        requestContext.copy(contextStorage = requestContext.contextStorage + Obj.from(annotation.target → v))
      case None ⇒
        requestContext.copy(contextStorage = requestContext.contextStorage - Lst.from(annotation.target))
    }
  }

  protected def fetchAndReturn(requestContext: RequestContext): Task[Option[Value]] = {
    val ctx = ExpressionEvaluatorContext(requestContext, Obj.empty)
    try {
      val location = expressionEvaluator.evaluate(ctx, annotation.location).toString
      val query = annotation.query.map { kv ⇒
        kv._1 → expressionEvaluator.evaluate(ctx, kv._2)
      }
      val hrl = HRL(location, query)

      implicit val mcx = requestContext
      ask(hrl, ctx).
        onErrorRecoverWith {
          case NonFatal(e) ⇒
            handleError(hrl.toString, ctx, e)
        }
    } catch {
      case NonFatal(e) ⇒
        handleError(annotation.location.source, ctx, e)
    }
  }
}

