package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.model.{FieldFilter, FieldFilterContext, RamlFieldFilterFactory}
import com.hypertino.facade.raml.RamlAnnotation
import monix.eval.Task


object RemoveFieldFilter extends FieldFilter {
  // Remove filter just removes the field
  def apply(context: FieldFilterContext): Task[Option[Value]] = Task.now {
    None
  }
}

class RemoveFieldFilterFactory extends RamlFieldFilterFactory {
  def createFieldFilter(typeName: String, fieldName: String, annotation: RamlAnnotation): FieldFilter = RemoveFieldFilter
}