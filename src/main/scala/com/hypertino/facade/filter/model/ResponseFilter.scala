/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filter.model
import com.hypertino.facade.model.RequestContext
import com.hypertino.hyperbus.model.DynamicResponse
import monix.eval.Task
import monix.execution.Scheduler

trait ResponseFilter extends Filter {
  def apply(requestContext: RequestContext, response: DynamicResponse)
           (implicit scheduler: Scheduler): Task[DynamicResponse]
}
