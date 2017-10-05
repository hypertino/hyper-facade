package com.hypertino.facade.filter.raml

import com.hypertino.facade.filter.model.{EventFilter, RequestFilter}
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.utils.{HrlTransformer, RequestUtils}
import com.hypertino.hyperbus.model.{DynamicRequest, HRL}
import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.{ExecutionContext, Future}

class RewriteRequestFilter(sourceHRL: HRL, destinationHRL: HRL, protected val expressionEvaluator: ExpressionEvaluator) extends RequestFilter {

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
  override def apply(requestContext: RequestContext, event: DynamicRequest)
                    (implicit scheduler: Scheduler): Task[DynamicRequest] = {
    val newHrl = HrlTransformer.rewriteBackward(event.headers.hrl, event.headers.method)
    Task.now(RequestUtils.copyWith(event, newHrl))
  }
}
