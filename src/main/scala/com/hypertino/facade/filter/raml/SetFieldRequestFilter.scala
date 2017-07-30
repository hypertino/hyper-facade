package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.raml.{Field, RamlAnnotation, SetAnnotation}
import monix.eval.Task

class SetFieldFilter(annotation: SetAnnotation, expressionEvaluator: ExpressionEvaluator) extends FieldFilter {
  def apply(rootValue: Value, field: Field, value: Option[Value], requestContext: RequestContext): Task[Option[Value]] = Task.now {
    Some(expressionEvaluator.evaluate(requestContext, annotation.source))
  }
}

class SetFieldFilterFactory(protected val predicateEvaluator: ExpressionEvaluator) extends RamlFieldFilterFactory {
  override def createFieldFilter(typeName: String, annotation: RamlAnnotation, field: Field): FieldFilter = {
    new SetFieldFilter(annotation.asInstanceOf[SetAnnotation], predicateEvaluator)
  }
}
