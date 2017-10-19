package com.hypertino.facade.filters.annotated

import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, PreparedExpression}
import com.hypertino.facade.raml.RamlFieldAnnotation
import monix.eval.Task

case class SetFieldAnnotation(
                         predicate: Option[PreparedExpression],
                         source: PreparedExpression,
                         stages: Set[FieldFilterStage]
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
