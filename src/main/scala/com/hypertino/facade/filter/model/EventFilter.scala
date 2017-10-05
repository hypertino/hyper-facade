package com.hypertino.facade.filter.model

import com.hypertino.facade.model.RequestContext
import com.hypertino.hyperbus.model.DynamicRequest
import monix.eval.Task
import monix.execution.Scheduler

trait EventFilter extends Filter {
  def apply(requestContext: RequestContext, event: DynamicRequest)
           (implicit scheduler: Scheduler): Task[DynamicRequest]
}
