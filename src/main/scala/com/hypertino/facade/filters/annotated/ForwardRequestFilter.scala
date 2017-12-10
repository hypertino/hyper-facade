/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.annotated

import com.hypertino.binders.value.Obj
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, ExpressionEvaluatorContext, PreparedExpression}
import com.hypertino.facade.metrics.MetricKeys
import com.hypertino.facade.model._
import com.hypertino.facade.utils.{HrlTransformer, RequestUtils}
import com.hypertino.hyperbus.model.HRL
import monix.eval.Task
import monix.execution.Scheduler

class ForwardRequestFilter(sourceHRL: HRL,
                           location: PreparedExpression,
                           query: Map[String, PreparedExpression],
                           method: Option[PreparedExpression],
                           protected val expressionEvaluator: ExpressionEvaluator) extends RequestFilter {
  val timer = Some(MetricKeys.specificFilter("ForwardRequestFilter"))
  override def apply(requestContext: RequestContext)
                    (implicit scheduler: Scheduler): Task[RequestContext] = {
    Task.now {
      val request = requestContext.request
      val ctx = ExpressionEvaluatorContext(requestContext, Obj.empty)
      val locationEvaluated = expressionEvaluator.evaluate(ctx, location).toString
      val queryEvaluated = query.map { kv ⇒
        kv._1 → expressionEvaluator.evaluate(ctx, kv._2)
      }
      val destinationHRL = HRL(locationEvaluated, queryEvaluated)
      val destinationMethod = method.map(expressionEvaluator.evaluate(ctx, _).toString)

      // todo: should we preserve all query fields???
      val rewrittenUri = HrlTransformer.rewriteForwardWithPatterns(request.headers.hrl, sourceHRL, destinationHRL)
      requestContext.copy(
        request = RequestUtils.copyWith(request, rewrittenUri, destinationMethod)
      )
    }
  }
}


