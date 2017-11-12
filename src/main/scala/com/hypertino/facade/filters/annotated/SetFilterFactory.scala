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
import com.hypertino.facade.raml._
import com.typesafe.config.Config
import scaldi.Injectable

case class SetAnnotation(
                          @fieldName("if") predicate: Option[PreparedExpression],
                          source: PreparedExpression,
                          target: Option[String]
                        ) extends RamlAnnotation {
  def name: String = "set"
}

class SetFilterFactory(config: Config, protected val predicateEvaluator: ExpressionEvaluator) extends RamlFilterFactory with Injectable {
  override def createFilters(target: RamlFilterTarget): SimpleFilterChain = {
    val set = target match {
      case ResourceTarget(_, set: SetAnnotation) ⇒ set
      case MethodTarget(_, _, set: SetAnnotation) ⇒ set
      case otherTarget ⇒ throw RamlConfigException(s"Annotation 'set' cannot be assigned to $otherTarget")
    }
    SimpleFilterChain(
      requestFilters = Seq(new SetRequestFilter(set, predicateEvaluator)),
      responseFilters = Seq.empty,
      eventFilters = Seq()
    )
  }

  override def createRamlAnnotation(name: String, value: Value): RamlAnnotation = {
    value.to[SetAnnotation]
  }
}
