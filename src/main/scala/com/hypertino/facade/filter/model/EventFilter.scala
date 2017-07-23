package com.hypertino.facade.filter.model

import com.hypertino.facade.model.RequestContext
import com.hypertino.hyperbus.model.DynamicRequest

import scala.concurrent.{ExecutionContext, Future}

trait EventFilter extends Filter {
  def apply(contextWithRequest: RequestContext, event: DynamicRequest)
           (implicit ec: ExecutionContext): Future[DynamicRequest]
}
