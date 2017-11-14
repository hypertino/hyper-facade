/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.annotated

import com.hypertino.binders.annotations.fieldName
import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, PreparedExpression}
import com.hypertino.facade.filters.chain.before_resolved.AuthorizationRequestFilter
import com.hypertino.facade.raml._
import com.hypertino.hyperbus.Hyperbus
import monix.execution.Scheduler
import scaldi.Injectable

case class AuthorizeAnnotation(
                          @fieldName("if") predicate: Option[PreparedExpression],
                          source: PreparedExpression,
                          mode: Option[String] = Some(AuthorizeAnnotation.MODE_NORMAL)
                        ) extends RamlAnnotation {
  def name: String = "authorize"
}

class AuthorizeFilterFactory(hyperbus: Hyperbus,
                             protected val scheduler: Scheduler,
                             protected val predicateEvaluator: ExpressionEvaluator) extends RamlFilterFactory with Injectable {
  override def createFilters(target: RamlFilterTarget): SimpleFilterChain = {
    val aa = target match {
      case ResourceTarget(_, aa: AuthorizeAnnotation) ⇒ aa
      case MethodTarget(_, _, aa: AuthorizeAnnotation) ⇒ aa
      case otherTarget ⇒ throw RamlConfigException(s"Annotation 'authorize' cannot be assigned to $otherTarget")
    }
    SimpleFilterChain(
      requestFilters = Seq(new AuthorizationRequestFilter(hyperbus, predicateEvaluator, scheduler, Some(aa))),
      responseFilters = Seq.empty,
      eventFilters = Seq()
    )
  }

  override def createRamlAnnotation(name: String, value: Value): RamlAnnotation = {
    value.to[AuthorizeAnnotation]
  }
}

object AuthorizeAnnotation {
  final val MODE_NORMAL = "normal"
  final val MODE_PRIVILEGE = "privilege"
}
