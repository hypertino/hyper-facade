package com.hypertino.facade.filter

import com.hypertino.binders.value.{Lst, Null, Obj, Value}
import com.hypertino.facade.filter.model.ResponseFilter
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model.RequestContext
import com.hypertino.facade.utils.{SelectField, SelectFields}
import com.hypertino.hyperbus.model.{DynamicBody, DynamicResponse, StandardResponse}
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler
import scala.util.control.NonFatal

class SelectFieldsResponseFilter(
                                  protected val expressionEvaluator: ExpressionEvaluator
                                ) extends ResponseFilter with StrictLogging {

  override def apply(requestContext: RequestContext, response: DynamicResponse)
                    (implicit scheduler: Scheduler): Task[DynamicResponse] = {
    Task.now {
      try {
        requestContext.request.headers.hrl.query.fields match {
          case Null ⇒
            response

          case fields: Value ⇒
            val selectFields = SelectFields(fields.toString)
            val bodyContent = SelectFieldsResponseFilter.filterFields(response.body.content, selectFields)
            StandardResponse(DynamicBody(bodyContent), response.headers).asInstanceOf[DynamicResponse]
        }
      }
      catch {
        case NonFatal(e) ⇒
          logger.error("Unhandled exception", e)
          throw e;
      }
    }
  }
}
object SelectFieldsResponseFilter {
  def filterFields(v: Value, selectFields: Map[String, SelectField]): Value = {
    recursiveFilterFields(v, selectFields)
  }

  private def recursiveFilterFields(v: Value, selectFields: Map[String, SelectField]): Value = {
    if (selectFields.nonEmpty) {
      v match {
        case Obj(inner) ⇒ Obj(
          inner.flatMap { case (k, i) ⇒
              selectFields.get(k).map {
                sf ⇒ k → recursiveFilterFields(i, sf.children)
              }.orElse(if(selectFields.contains("*")) {
                Option(k -> i)
              } else {
                None
              })
            })

        case Lst(inner) ⇒
          Lst(
            inner.map { i ⇒
              recursiveFilterFields(i, selectFields)
            }
          )

        case _ ⇒ v
      }
    }
    else {
      v
    }
  }
}