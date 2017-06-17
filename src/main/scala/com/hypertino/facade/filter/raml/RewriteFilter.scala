package com.hypertino.facade.filter.raml

import com.hypertino.facade.filter.model.{EventFilter, RequestFilter}
import com.hypertino.facade.model._
import com.hypertino.facade.utils.HrlTransformer
import com.hypertino.hyperbus.transport.api.uri.Uri

import scala.concurrent.{ExecutionContext, Future}

class RewriteRequestFilter(val uri: String) extends RequestFilter {
  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    Future {
      val request = contextWithRequest.request
      val rewrittenUri = HrlTransformer.rewrite(request.uri, Uri(uri))
      val rewrittenRequest = request.copy(
        uri = rewrittenUri
      )
      contextWithRequest.copy(
        request = rewrittenRequest
      )
    }
  }
}

class RewriteEventFilter extends EventFilter {
  override def apply(contextWithRequest: ContextWithRequest, event: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val newUri = HrlTransformer.rewriteBackward(event.uri, event.method)
    Future.successful(event.copy(uri = newUri))
  }
}
