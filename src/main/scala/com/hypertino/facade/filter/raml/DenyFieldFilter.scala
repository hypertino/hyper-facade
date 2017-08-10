package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.model.{FieldFilter, FieldFilterContext, RamlFieldFilterFactory}
import com.hypertino.facade.raml.RamlAnnotation
import com.hypertino.hyperbus.model.{ErrorBody, Forbidden}
import monix.eval.Task

// todo: add statusCode to support different replies
class DenyFieldFilter(typeName: String, fieldName: String) extends FieldFilter {
  def apply(context: FieldFilterContext): Task[Option[Value]] = Task.raiseError {
    implicit val mcx = context.requestContext
    Forbidden(ErrorBody("field-is-protected", Some(s"You can't set field `$typeName.$fieldName`")))
  }
}

class DenyFieldFilterFactory extends RamlFieldFilterFactory {
  def createFieldFilter(typeName: String, fieldName: String, annotation: RamlAnnotation): FieldFilter = new DenyFieldFilter(
    typeName, fieldName
  )
}