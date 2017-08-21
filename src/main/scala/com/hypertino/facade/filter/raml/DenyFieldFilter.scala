package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.model._
import com.hypertino.facade.raml.{RamlAnnotation, RamlFieldAnnotation}
import com.hypertino.hyperbus.model.{ErrorBody, Forbidden}
import monix.eval.Task

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
}