package com.hypertino.facade.filter.raml

import com.hypertino.facade.filter.model.{EventFilter, RequestFilter}
import com.hypertino.facade.filter.parser.PredicateEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.utils.{HrlTransformer, RequestUtils}
import com.hypertino.hyperbus.model.{DynamicRequest, HRL}

import scala.concurrent.{ExecutionContext, Future}

class RewriteRequestFilter(uri: String, protected val predicateEvaluator: PredicateEvaluator) extends RequestFilter {
  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    Future {
      val request = contextWithRequest.request
      // todo: should we preserve all query fields???
      val rewrittenUri = HrlTransformer.rewrite(request.headers.hrl, HRL(uri, request.headers.hrl.query))
      contextWithRequest.copy(
        request = RequestUtils.copyWithNewHRL(request, rewrittenUri)
      )
    }
  }
}

class RewriteEventFilter(protected val predicateEvaluator: PredicateEvaluator) extends EventFilter {
  override def apply(contextWithRequest: ContextWithRequest, event: DynamicRequest)
                    (implicit ec: ExecutionContext): Future[DynamicRequest] = {
    val newHrl = HrlTransformer.rewriteBackward(event.headers.hrl, event.headers.method)
    Future.successful(RequestUtils.copyWithNewHRL(event, newHrl))
  }
}
