package com.hypertino.facade.filter.model

import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.parser.ExpressionEvaluatorContext
import com.hypertino.facade.model.RequestContext
import com.hypertino.facade.raml.Field
import monix.eval.Task

sealed trait FieldFilterStage {
  def stringValue: String
}
case object FieldFilterStageRequest extends FieldFilterStage {
  def stringValue: String = "request"
}
case object FieldFilterStageResponse extends FieldFilterStage {
  def stringValue: String = "response"
}
case object FieldFilterStageEvent extends FieldFilterStage {
  def stringValue: String = "event"
}

object FieldFilterStage {
  def apply(s: String): FieldFilterStage = s match {
    case str if str == FieldFilterStageRequest.stringValue ⇒ FieldFilterStageRequest
    case str if str == FieldFilterStageResponse.stringValue ⇒ FieldFilterStageResponse
    case str if str == FieldFilterStageEvent.stringValue ⇒ FieldFilterStageEvent
    case _ ⇒ throw new IllegalArgumentException(s"'$s' doesn't represents a stage [request/response/event]")
  }
}

case class FieldFilterContext(
                               fieldPath: Seq[String],
                               value: Option[Value],
                               field: Field,
                               extraContext: Value,
                               requestContext: RequestContext,
                               stage: FieldFilterStage
                             ) {
  lazy val expressionEvaluatorContext = ExpressionEvaluatorContext(requestContext,extraContext)
}

trait FieldFilter {
  def apply(context: FieldFilterContext): Task[Option[Value]]
}
