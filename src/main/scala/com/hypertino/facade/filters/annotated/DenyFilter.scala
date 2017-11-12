/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.annotated

import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model._
import com.hypertino.hyperbus.model.{ErrorBody, Forbidden}
import monix.eval.Task
import monix.execution.Scheduler

class DenyRequestFilter(protected val expressionEvaluator: ExpressionEvaluator) extends RequestFilter {

  override def apply(requestContext: RequestContext)
                    (implicit scheduler: Scheduler): Task[RequestContext] = {
    Task.raiseError {
      implicit val mcx = requestContext.request
      Forbidden(ErrorBody("forbidden", Some(s"Access to resource ${requestContext.request.headers.hrl} is forbidden")))
    }
  }
}
