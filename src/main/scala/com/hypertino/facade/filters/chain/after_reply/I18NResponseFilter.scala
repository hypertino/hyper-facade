/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.chain.after_reply

import com.hypertino.binders.value.{Lst, Null, Obj, Text, Value}
import com.hypertino.facade.filter.model.ResponseFilter
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.metrics.MetricKeys
import com.hypertino.facade.model.RequestContext
import com.hypertino.hyperbus.model.{DynamicBody, DynamicResponse, StandardResponse}
import com.hypertino.langutils.{LanguageRanges, ValueI18N}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler
import spray.http.parser.HttpParser

import scala.util.control.NonFatal

class I18NResponseFilter(
                          protected val config: Config,
                          protected val expressionEvaluator: ExpressionEvaluator
                        ) extends ResponseFilter with StrictLogging {

  private val defaultLocale = config.getString("hyperfacade.i18n-filter.default-locale")
  private val postfix = config.getString("hyperfacade.i18n-filter.fields-postfix")
  val timer = Some(MetricKeys.specificFilter("I18NResponseFilter"))

  override def apply(requestContext: RequestContext, response: DynamicResponse)
                    (implicit scheduler: Scheduler): Task[DynamicResponse] = {
    Task.now {
      try {
        val acceptLanguage = requestContext.httpHeaders.get("accept-language").map(_.toString).getOrElse(defaultLocale)
        val lr = LanguageRanges(acceptLanguage)
        val bodyContent = ValueI18N.localize(response.body.content, lr, postfix=postfix)
        StandardResponse(DynamicBody(bodyContent), response.headers).asInstanceOf[DynamicResponse]
      }
      catch {
        case NonFatal(e) ⇒
          logger.error("Unhandled exception", e)
          throw e;
      }
    }
  }
}
