package com.hypertino.facade.filter.model

import com.hypertino.binders.value.{Obj, Value}
import com.hypertino.facade.model.RequestContext
import com.hypertino.facade.raml.Field
import monix.eval.Task

case class FieldFilterContext(
                               path: String,
                               value: Option[Value],
                               field: Field,
                               extraContext: Value,
                               requestContext: RequestContext
                             )

trait FieldFilter {
  def apply(context: FieldFilterContext): Task[Option[Value]]
}
