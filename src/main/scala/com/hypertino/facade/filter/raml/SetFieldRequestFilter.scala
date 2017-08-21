package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.raml.{Field, RamlAnnotation, SetAnnotation}
import monix.eval.Task

class SetFieldFilter(annotation: SetAnnotation, expressionEvaluator: ExpressionEvaluator) extends FieldFilter {
  def apply(context: FieldFilterContext): Task[Option[Value]] = if (context.stage == FieldFilterStageRequest) Task.now {
    Some(expressionEvaluator.evaluate(context.requestContext, context.extraContext, annotation.source))
  } else {
    Task.now(context.value)
  }
}

class SetFieldFilterFactory(protected val predicateEvaluator: ExpressionEvaluator) extends RamlFieldFilterFactory {
  def createFieldFilter(fieldName: String, typeName: String, annotation: RamlAnnotation): FieldFilter = {
    new SetFieldFilter(annotation.asInstanceOf[SetAnnotation], predicateEvaluator)
  }
}
