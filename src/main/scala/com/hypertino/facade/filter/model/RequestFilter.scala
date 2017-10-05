package com.hypertino.facade.filter.model

import com.hypertino.facade.model.RequestContext
import monix.eval.Task
import monix.execution.Scheduler

trait RequestFilter extends Filter {
  def apply(requestContext: RequestContext)
           (implicit scheduler: Scheduler): Task[RequestContext]
}
