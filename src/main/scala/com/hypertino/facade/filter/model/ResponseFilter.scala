package com.hypertino.facade.filter.model

import com.hypertino.facade.model.RequestContext
import com.hypertino.hyperbus.model.DynamicResponse
import monix.eval.Task
import monix.execution.Scheduler

trait ResponseFilter extends Filter {
  def apply(requestContext: RequestContext, response: DynamicResponse)
           (implicit scheduler: Scheduler): Task[DynamicResponse]
}
