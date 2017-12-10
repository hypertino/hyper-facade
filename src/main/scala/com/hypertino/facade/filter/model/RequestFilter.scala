/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filter.model

import com.codahale.metrics.Timer
import com.hypertino.facade.model.RequestContext
import monix.eval.Task
import monix.execution.Scheduler

trait RequestFilter extends Filter {
  def apply(requestContext: RequestContext)
           (implicit scheduler: Scheduler): Task[RequestContext]
}
