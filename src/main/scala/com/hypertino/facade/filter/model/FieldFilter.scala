package com.hypertino.facade.filter.model

import com.hypertino.binders.value.{Obj, Value}
import com.hypertino.facade.model.RequestContext
import com.hypertino.facade.raml.Field
import com.hypertino.hyperbus.model.MessagingContext
import monix.eval.Task

sealed trait FieldFilterStage
case object FieldFilterStageRequest extends FieldFilterStage
case object FieldFilterStageResponse extends FieldFilterStage
case object FieldFilterStageEvent extends FieldFilterStage

case class FieldFilterContext(
                               fieldPath: Seq[String],
                               value: Option[Value],
                               field: Field,
                               extraContext: Value,
                               requestContext: RequestContext,
                               stage: FieldFilterStage
                             )

trait FieldFilter {
  def apply(context: FieldFilterContext): Task[Option[Value]]
}
