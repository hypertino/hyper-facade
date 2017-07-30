package com.hypertino.facade.filter.model

import com.hypertino.binders.value.Value
import com.hypertino.facade.model.RequestContext
import com.hypertino.facade.raml.Field
import monix.eval.Task

trait FieldFilter {
  def apply(rootValue: Value, field: Field, value: Option[Value], requestContext: RequestContext): Task[Option[Value]]
}
