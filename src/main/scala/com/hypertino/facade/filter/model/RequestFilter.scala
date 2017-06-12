package com.hypertino.facade.filter.model

import com.hypertino.facade.model.ContextWithRequest

import scala.concurrent.{ExecutionContext, Future}


trait RequestFilter extends Filter {
  def apply(contextWithRequest: ContextWithRequest)
           (implicit ec: ExecutionContext): Future[ContextWithRequest]
}
