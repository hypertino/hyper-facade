package com.hypertino.facade.filter.model

import com.hypertino.facade.model.{ContextWithRequest, FacadeResponse}

import scala.concurrent.{ExecutionContext, Future}

trait ResponseFilter extends Filter {
  def apply(contextWithRequest: ContextWithRequest, response: FacadeResponse)
           (implicit ec: ExecutionContext): Future[FacadeResponse]
}
