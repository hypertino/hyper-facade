package com.hypertino.facade.utils

import com.hypertino.metrics.MetricsTracker
import monix.eval.Task
import monix.execution.Scheduler

object MetricUtils {
  def timeOfTask[A](name: String, tracker: MetricsTracker, f: ⇒ Task[A])
                   (implicit scheduler: Scheduler): Task[A] = {
    val timerContext = tracker.timer(name).time()
    f.materialize.map { result =>
      timerContext.close()
      result
    }.dematerialize
  }
}
