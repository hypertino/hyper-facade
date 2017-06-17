package com.hypertino.facade.filter.model

import com.hypertino.facade.model.ContextWithRequest
import com.hypertino.hyperbus.model.DynamicResponse

import scala.concurrent.{ExecutionContext, Future}

trait ResponseFilter extends Filter {
  def apply(contextWithRequest: ContextWithRequest, response: DynamicResponse)
           (implicit ec: ExecutionContext): Future[DynamicResponse]
}
