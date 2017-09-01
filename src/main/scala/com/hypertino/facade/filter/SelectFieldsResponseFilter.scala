package com.hypertino.facade.filter

import ch.qos.logback.classic.selector.servlet.LoggerContextFilter
import com.hypertino.binders.value.{Lst, Null, Obj, Value}
import com.hypertino.facade.FacadeConfigPaths
import com.hypertino.facade.filter.http.HttpWsFilter
import com.hypertino.facade.filter.model.ResponseFilter
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model.{FacadeHeaders, RequestContext}
import com.hypertino.facade.utils.{SelectField, SelectFields}
import com.hypertino.hyperbus.model.{DynamicBody, DynamicMessage, DynamicResponse, HRL, Headers, HeadersMap, ResponseHeaders, StandardResponse}
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class SelectFieldsResponseFilter(
                                  protected val expressionEvaluator: ExpressionEvaluator
                                ) extends ResponseFilter {

  protected val log = LoggerFactory.getLogger(getClass)

  override def apply(contextWithRequest: RequestContext, response: DynamicResponse)
                    (implicit ec: ExecutionContext): Future[DynamicResponse] = {
    Future {
      try {
        contextWithRequest.request.headers.hrl.query.fields match {
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
          log.error("Unhandled exception", e)
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
              }.orElse {
                selectFields.get("*").map { _ ⇒
                  k → i
                }
              }
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