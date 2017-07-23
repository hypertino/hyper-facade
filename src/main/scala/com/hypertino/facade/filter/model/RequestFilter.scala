package com.hypertino.facade.filter.model

import com.hypertino.facade.model.RequestContext

import scala.concurrent.{ExecutionContext, Future}


trait RequestFilter extends Filter {
  def apply(contextWithRequest: RequestContext)
           (implicit ec: ExecutionContext): Future[RequestContext]
}
