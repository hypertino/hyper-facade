package com.hypertino.facade.filter.raml

import com.hypertino.facade.filter.model.{EventFilter, RequestFilter}
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.utils.{HrlTransformer, RequestUtils}
import com.hypertino.hyperbus.model.{DynamicRequest, HRL}

import scala.concurrent.{ExecutionContext, Future}

class RewriteRequestFilter(sourceHRL: HRL, destinationHRL: HRL, protected val expressionEvaluator: ExpressionEvaluator) extends RequestFilter {

  override def apply(contextWithRequest: RequestContext)
                    (implicit ec: ExecutionContext): Future[RequestContext] = {
    Future {
      val request = contextWithRequest.request
      // todo: should we preserve all query fields???
      val rewrittenUri = HrlTransformer.rewrite(request.headers.hrl, sourceHRL, destinationHRL)
      contextWithRequest.copy(
        request = RequestUtils.copyWithNewHRL(request, rewrittenUri)
      )
    }
  }
}

class RewriteEventFilter(protected val expressionEvaluator: ExpressionEvaluator) extends EventFilter {
  override def apply(contextWithRequest: RequestContext, event: DynamicRequest)
                    (implicit ec: ExecutionContext): Future[DynamicRequest] = {
    val newHrl = HrlTransformer.rewriteBackward(event.headers.hrl, event.headers.method)
    Future.successful(RequestUtils.copyWithNewHRL(event, newHrl))
  }
}
