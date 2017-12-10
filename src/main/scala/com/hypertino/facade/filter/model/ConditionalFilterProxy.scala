/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filter.model

import com.hypertino.binders.value.Obj
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, ExpressionEvaluatorContext}
import com.hypertino.facade.metrics.MetricKeys
import com.hypertino.facade.model._
import com.hypertino.facade.raml.RamlAnnotation
import com.hypertino.hyperbus.model.{DynamicRequest, DynamicResponse}
import monix.eval.Task
import monix.execution.Scheduler

import scala.util.{Failure, Success, Try}

case class ConditionalRequestFilterProxy(annotation: RamlAnnotation, filter: RequestFilter,
                                         protected val expressionEvaluator: ExpressionEvaluator) extends RequestFilter {

  val timer = annotation.predicate.map(p ⇒ MetricKeys.specificFilter("if-/"+p.source))

  override def apply(requestContext: RequestContext)
                    (implicit scheduler: Scheduler): Task[RequestContext] = {
    annotation.predicate match {
      case Some(p) ⇒
        Try(filter.evaluatePredicate(ExpressionEvaluatorContext(requestContext, Obj.empty), p)) match {
          case Success(true) ⇒
            filter.apply(requestContext)
          case Success(false) ⇒
            Task.now(requestContext)
          case Failure(ex) ⇒
            Task.raiseError(ex)
        }
      case None ⇒
        filter.apply(requestContext)
    }
  }
}

case class ConditionalResponseFilterProxy(annotation: RamlAnnotation, filter: ResponseFilter,
                                          protected val expressionEvaluator: ExpressionEvaluator) extends ResponseFilter {

  val timer = annotation.predicate.map(p ⇒ MetricKeys.specificFilter("if-/"+p.source))

  override def apply(requestContext: RequestContext, response: DynamicResponse)
                    (implicit scheduler: Scheduler): Task[DynamicResponse] = {
    annotation.predicate match {
      case Some(p) ⇒
        Try(filter.evaluatePredicate(ExpressionEvaluatorContext(requestContext, Obj.empty), p)) match {
          case Success(true) ⇒
            filter.apply(requestContext, response)
          case Success(false) ⇒
            Task.now(response)
          case Failure(ex) ⇒
            Task.raiseError(ex)
        }

      case None ⇒
        filter.apply(requestContext, response)
    }
  }
}

case class ConditionalEventFilterProxy(annotation: RamlAnnotation, filter: EventFilter,
                                       protected val expressionEvaluator: ExpressionEvaluator) extends EventFilter {

  val timer = annotation.predicate.map(p ⇒ MetricKeys.specificFilter("if-/"+p.source))

  override def apply(requestContext: RequestContext, event: DynamicRequest)
                    (implicit scheduler: Scheduler): Task[DynamicRequest] = {
    annotation.predicate match {
      case Some(p) ⇒
        Try(filter.evaluatePredicate(ExpressionEvaluatorContext(requestContext, Obj.empty), p)) match {
          case Success(true) ⇒
            filter.apply(requestContext, event)
          case Success(false) ⇒
            Task.now(event)
          case Failure(ex) ⇒
            Task.raiseError(ex)
        }

      case None ⇒
        filter.apply(requestContext, event)
    }
  }
}
