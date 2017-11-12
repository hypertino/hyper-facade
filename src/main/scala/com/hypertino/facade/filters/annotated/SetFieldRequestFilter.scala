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
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, PreparedExpression}
import com.hypertino.facade.raml.RamlFieldAnnotation
import monix.eval.Task

case class SetFieldAnnotation(
                               @fieldName("if") predicate: Option[PreparedExpression],
                               source: PreparedExpression,
                               stages: Set[FieldFilterStage] = Set(FieldFilterStageRequest)
                             ) extends RamlFieldAnnotation {
  def name: String = "set"
}


class SetFieldFilter(annotation: SetFieldAnnotation, expressionEvaluator: ExpressionEvaluator) extends FieldFilter {
  def apply(context: FieldFilterContext): Task[Option[Value]] = Task.now {
    Some(expressionEvaluator.evaluate(context.expressionEvaluatorContext, annotation.source))
  }
}

class SetFieldFilterFactory(protected val predicateEvaluator: ExpressionEvaluator) extends RamlFieldFilterFactory {
  def createFieldFilter(fieldName: String, typeName: String, annotation: RamlFieldAnnotation): FieldFilter = {
    new SetFieldFilter(annotation.asInstanceOf[SetFieldAnnotation], predicateEvaluator)
  }

  override def createRamlAnnotation(name: String, value: Value): RamlFieldAnnotation = {
    value.to[SetFieldAnnotation]
  }
}
