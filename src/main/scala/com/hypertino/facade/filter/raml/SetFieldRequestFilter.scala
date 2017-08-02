package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.raml.{Field, RamlAnnotation, SetAnnotation}
import monix.eval.Task

class SetFieldFilter(annotation: SetAnnotation, expressionEvaluator: ExpressionEvaluator) extends FieldFilter {
  def apply(context: FieldFilterContext): Task[Option[Value]] = Task.now {
    Some(expressionEvaluator.evaluate(context.requestContext, context.extraContext, annotation.source))
  }
}

class SetFieldFilterFactory(protected val predicateEvaluator: ExpressionEvaluator) extends RamlFieldFilterFactory {
  def createFieldFilter(typeName: String, fieldName: String, annotation: RamlAnnotation): FieldFilter = {
    new SetFieldFilter(annotation.asInstanceOf[SetAnnotation], predicateEvaluator)
  }
}
