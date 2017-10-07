package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.PreparedExpression
import com.hypertino.facade.raml.{RamlAnnotation, RamlFieldAnnotation}
import monix.eval.Task

case class RemoveFieldAnnotation(
                            predicate: Option[PreparedExpression],
                            stages: Set[FieldFilterStage]
                           ) extends RamlFieldAnnotation {
  def name: String = "remove"
}

object RemoveFieldFilter extends FieldFilter {
  // Remove filter just removes the field
  def apply(context: FieldFilterContext): Task[Option[Value]] = Task.now {
    None
  }
}

class RemoveFieldFilterFactory extends RamlFieldFilterFactory {
  def createFieldFilter(fieldName: String, typeName: String, annotation: RamlFieldAnnotation): FieldFilter = RemoveFieldFilter

  override def createRamlAnnotation(name: String, value: Value): RamlFieldAnnotation = {
    import com.hypertino.hyperbus.serialization.SerializationOptions._
    import PreparedExpression._
    import FieldFilterStage._
    value.to[RemoveFieldAnnotation]
  }
}