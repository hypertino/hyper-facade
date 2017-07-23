package com.hypertino.facade.filter.raml

import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.PredicateEvaluator
import com.hypertino.facade.model._
import com.hypertino.hyperbus.model.{ErrorBody, Forbidden}

import scala.concurrent.{ExecutionContext, Future}

class DenyRequestFilter(protected val predicateEvaluator: PredicateEvaluator) extends RequestFilter {

  override def apply(contextWithRequest: RequestContext)
                    (implicit ec: ExecutionContext): Future[RequestContext] = {
    Future.failed {
      implicit val mcx = contextWithRequest.request
      Forbidden(ErrorBody("forbidden", Some(s"Access to resource ${contextWithRequest.request.headers.hrl} is forbidden")))
    }
  }
}
