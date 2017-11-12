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
import com.hypertino.facade.filter.chain.{FilterChain, SimpleFilterChain}
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, PreparedExpression}
import com.hypertino.facade.raml.RamlAnnotation
import com.typesafe.scalalogging.StrictLogging
import scaldi.Injectable

case class DenyAnnotation(
                           @fieldName("if") predicate: Option[PreparedExpression]
                         ) extends RamlAnnotation {
  def name: String = "deny"
}

class DenyFilterFactory(protected val predicateEvaluator: ExpressionEvaluator) extends RamlFilterFactory with Injectable with StrictLogging{
  override def createFilters(target: RamlFilterTarget): SimpleFilterChain = {
    target match {
      case ResourceTarget(_, _ : DenyAnnotation) ⇒
        SimpleFilterChain(
          requestFilters = Seq(new DenyRequestFilter(predicateEvaluator)),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )

      case MethodTarget(_, _, _ : DenyAnnotation) ⇒
        SimpleFilterChain(
          requestFilters = Seq(new DenyRequestFilter(predicateEvaluator)),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )

      case unknownTarget ⇒
        logger.warn(s"Annotation (deny) is not supported for target $unknownTarget. Empty filter chain will be created")
        FilterChain.empty
    }
  }

  override def createRamlAnnotation(name: String, value: Value): RamlAnnotation = {
    value.to[DenyAnnotation]
  }
}
