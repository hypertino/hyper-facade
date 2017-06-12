package com.hypertino.facade.filter.model

import com.hypertino.facade.model.{ContextWithRequest, FacadeRequest}

import scala.concurrent.{ExecutionContext, Future}

trait EventFilter extends Filter {
  def apply(contextWithRequest: ContextWithRequest, event: FacadeRequest)
           (implicit ec: ExecutionContext): Future[FacadeRequest]
}
