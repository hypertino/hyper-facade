package com.hypertino.facade.filters.annotated

import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.PreparedExpression
import com.hypertino.facade.raml.RamlFieldAnnotation
import com.hypertino.hyperbus.model.{ErrorBody, Forbidden}
import monix.eval.Task

case class DenyFieldAnnotation(
                           predicate: Option[PreparedExpression],
                           stages: Set[FieldFilterStage] = Set(FieldFilterStageRequest)
                         ) extends RamlFieldAnnotation {
  def name: String = "deny"
}

// todo: add statusCode to support different replies
class DenyFieldFilter(fieldName: String, typeName: String) extends FieldFilter {
  def apply(context: FieldFilterContext): Task[Option[Value]] = {
    if (context.value.isDefined) Task.raiseError {
      implicit val mcx = context.requestContext
      Forbidden(ErrorBody("field-is-protected", Some(s"You can't set field `$fieldName`")))
    } else Task.now {
      None
    }
  }
}

class DenyFieldFilterFactory extends RamlFieldFilterFactory {
  def createFieldFilter(fieldName: String, typeName: String, annotation: RamlFieldAnnotation): FieldFilter = new DenyFieldFilter(
    fieldName, typeName
  )

  override def createRamlAnnotation(name: String, value: Value): RamlFieldAnnotation = {
    value.to[DenyFieldAnnotation]
  }
}