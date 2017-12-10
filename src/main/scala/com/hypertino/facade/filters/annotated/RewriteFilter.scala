/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.annotated

import com.hypertino.facade.filter.model.{EventFilter, RequestFilter}
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.metrics.MetricKeys
import com.hypertino.facade.model._
import com.hypertino.facade.utils.{HrlTransformer, RequestUtils}
import com.hypertino.hyperbus.model.{DynamicRequest, HRL}
import monix.eval.Task
import monix.execution.Scheduler

class RewriteRequestFilter(sourceHRL: HRL, destinationHRL: HRL, protected val expressionEvaluator: ExpressionEvaluator) extends RequestFilter {
  val timer = Some(MetricKeys.specificFilter("RewriteRequestFilter"))
  override def apply(requestContext: RequestContext)
                    (implicit scheduler: Scheduler): Task[RequestContext] = {
    Task.now {
      val request = requestContext.request
      // todo: should we preserve all query fields???
      val rewrittenUri = HrlTransformer.rewriteForwardWithPatterns(request.headers.hrl, sourceHRL, destinationHRL)
      requestContext.copy(
        request = RequestUtils.copyWith(request, rewrittenUri)
      )
    }
  }
}

class RewriteEventFilter(protected val expressionEvaluator: ExpressionEvaluator) extends EventFilter {
  val timer = Some(MetricKeys.specificFilter("RewriteEventFilter"))
  override def apply(requestContext: RequestContext, event: DynamicRequest)
                    (implicit scheduler: Scheduler): Task[DynamicRequest] = {
    val newHrl = HrlTransformer.rewriteBackward(event.headers.hrl, event.headers.method)
    Task.now(RequestUtils.copyWith(event, newHrl))
  }
}
