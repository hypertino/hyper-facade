package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.model.{FieldFilter, RamlFieldFilterFactory}
import com.hypertino.facade.model.RequestContext
import com.hypertino.facade.raml.{Field, RamlAnnotation}
import monix.eval.Task

import scala.concurrent.{ExecutionContext, Future}


object RemoveFieldFilter extends FieldFilter {
  // Remove filter just removes the field
  def apply(rootValue: Value, field: Field, value: Option[Value], requestContext: RequestContext): Task[Option[Value]] = Task.now {
    None
  }
}

class RemoveFieldFilterFactory extends RamlFieldFilterFactory {
  override def createFieldFilter(typeName: String, annotation: RamlAnnotation, field: Field): FieldFilter = RemoveFieldFilter
}