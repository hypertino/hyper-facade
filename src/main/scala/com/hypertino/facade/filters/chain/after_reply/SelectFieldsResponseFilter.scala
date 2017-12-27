/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.chain.after_reply

import com.hypertino.binders.value.{Lst, Null, Obj, Value}
import com.hypertino.facade.filter.model.ResponseFilter
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.metrics.MetricKeys
import com.hypertino.facade.model.RequestContext
import com.hypertino.facade.utils.{SelectField, SelectFields}
import com.hypertino.hyperbus.model.{DynamicBody, DynamicResponse, StandardResponse}
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler

import scala.util.control.NonFatal

class SelectFieldsResponseFilter(
                                  protected val expressionEvaluator: ExpressionEvaluator
                                ) extends ResponseFilter with StrictLogging {

  val timer = Some(MetricKeys.specificFilter("SelectFieldsResponseFilter"))

  override def apply(requestContext: RequestContext, response: DynamicResponse)
                    (implicit scheduler: Scheduler): Task[DynamicResponse] = {
    Task.now {
      try {
        requestContext.request.headers.hrl.query.dynamic.fields match {
          case Null ⇒
            response

          case fields: Value ⇒
            val selectFields = SelectFields(fields.toString)
            val bodyContent = SelectFieldsResponseFilter.filterFields(response.body.content, selectFields)
            StandardResponse(DynamicBody(bodyContent), response.headers).asInstanceOf[DynamicResponse]
        }
      }
      catch {
        case NonFatal(e) ⇒
          logger.error("Unhandled exception", e)
          throw e;
      }
    }
  }
}

object SelectFieldsResponseFilter {
  def filterFields(v: Value, selectFields: Map[String, SelectField]): Value = {
    recursiveFilterFields(v, selectFields).getOrElse(Null)
  }

  private def recursiveFilterFields(v: Value, selectFields: Map[String, SelectField]): Option[Value] = {
    if (selectFields.nonEmpty) {
      v match {
        case Obj(inner) ⇒
          val res = inner.flatMap { case (k, i) ⇒
            val matchedFields = selectFields.get("**")
              .orElse(selectFields.get("*"))
              .orElse(selectFields.get(k))

            matchedFields.flatMap { sf ⇒
              recursiveFilterFields(i, sf.children).map(k → _)
            }
          }
          if (res.isEmpty) {
            None
          } else {
            Some(Obj(res))
          }

        case Lst(inner) ⇒
          Some(Lst(
            inner.flatMap { i ⇒
              recursiveFilterFields(i, selectFields)
            }
          ))

        case _ ⇒ None
      }
    }
    else {
      Some(v)
    }
  }
}