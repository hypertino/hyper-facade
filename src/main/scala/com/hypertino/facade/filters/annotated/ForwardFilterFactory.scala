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
import com.hypertino.hyperbus.model.HRL
import com.typesafe.config.Config
import scaldi.Injectable

case class ForwardAnnotation(
                              @fieldName("if") predicate: Option[PreparedExpression],
                              location: PreparedExpression,
                              query: Map[String, PreparedExpression],
                              method: Option[PreparedExpression]
                            ) extends RamlAnnotation {
  def name: String = "forward"
}

class ForwardFilterFactory(config: Config, protected val predicateEvaluator: ExpressionEvaluator) extends RamlFilterFactory with Injectable {
  override def createFilters(target: RamlFilterTarget): SimpleFilterChain = {
    val (sourceLocation, ramlMethod, destLocation, destQuery, destMethod) = target match {
      case ResourceTarget(uri, ForwardAnnotation(_, l, q, m)) ⇒ (uri, None, l, q, m)
      case MethodTarget(uri, method, ForwardAnnotation(_, l, q, m)) ⇒ (uri, Some(Method(method)), l, q, m)
      case otherTarget ⇒ throw RamlConfigException(s"Annotation 'forward' cannot be assigned to $otherTarget")
    }
    val sourceHRL = HRL(sourceLocation)
    SimpleFilterChain(
      requestFilters = Seq(new ForwardRequestFilter(sourceHRL, destLocation, destQuery, destMethod, predicateEvaluator)),
      responseFilters = Seq.empty,
      eventFilters = Seq.empty
    )
  }

  override def createRamlAnnotation(name: String, value: Value): RamlAnnotation = {
    value.to[ForwardAnnotation]
  }
}
