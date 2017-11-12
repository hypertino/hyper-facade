/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

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
