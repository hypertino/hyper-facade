/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.utils

import com.codahale.metrics.Timer
import com.hypertino.metrics.MetricsTracker
import monix.eval.Task
import monix.execution.Scheduler

object MetricUtils {
  def timeOfTask[A](name: String, tracker: MetricsTracker, f: ⇒ Task[A])
                   (implicit scheduler: Scheduler): Task[A] = timeOfTask(tracker.timer(name).time(), tracker, f)

  def timeOfTask[A](timerContext: Timer.Context, tracker: MetricsTracker, f: ⇒ Task[A])
                   (implicit scheduler: Scheduler): Task[A] = {
    f.doOnFinish {
      case _ ⇒ Task.now(timerContext.stop())
    }
    .doOnCancel {
      Task.now(timerContext.stop())
    }
  }

  implicit class TrackerUtilsExt(val tracker: MetricsTracker) extends AnyVal {
    def timeOfTask[T](name: String)(t: Task[T])(implicit scheduler: Scheduler): Task[T] = MetricUtils.timeOfTask(name, tracker, t)
    def timeOfTask[T](timerContext: Timer.Context)(t: Task[T])(implicit scheduler: Scheduler): Task[T] = MetricUtils.timeOfTask(timerContext, tracker, t)
  }
}
