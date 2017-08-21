package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.model.{FieldFilter, FieldFilterContext, FieldFilterStageResponse, RamlFieldFilterFactory}
import com.hypertino.facade.raml.{RamlAnnotation, RamlFieldAnnotation}
import monix.eval.Task


object RemoveFieldFilter extends FieldFilter {
  // Remove filter just removes the field
  def apply(context: FieldFilterContext): Task[Option[Value]] = Task.now {
    None
  }
}

class RemoveFieldFilterFactory extends RamlFieldFilterFactory {
  def createFieldFilter(fieldName: String, typeName: String, annotation: RamlFieldAnnotation): FieldFilter = RemoveFieldFilter
}