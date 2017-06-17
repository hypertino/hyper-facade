package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Obj, Value}
import com.hypertino.facade.filter.model.{EventFilter, RequestFilter, ResponseFilter}
import com.hypertino.facade.filter.parser.PredicateEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.raml.{DenyAnnotation, Field, RamlAnnotation}
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicResponse, ErrorBody, Forbidden, StandardResponse}

import scala.collection.Map
import scala.concurrent.{ExecutionContext, Future}

class DenyRequestFilter extends RequestFilter {

  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    Future {
      implicit val mcx = contextWithRequest.request
      val error = Forbidden(ErrorBody("forbidden"))
      throw new FilterInterruptException(
        error,
        s"Access to resource ${contextWithRequest.request.headers.hrl} is forbidden"
      )
    }
  }
}

class DenyResponseFilter(val field: Field, predicateEvaluator: PredicateEvaluator) extends ResponseFilter {

  override def apply(contextWithRequest: ContextWithRequest, response: DynamicResponse)
                    (implicit ec: ExecutionContext): Future[DynamicResponse] = {
    Future {
      StandardResponse(body = DynamicBody(DenyFilter.filterBody(field, response.body.content, contextWithRequest, predicateEvaluator)), response.headers)
        .asInstanceOf[DynamicResponse]
    }
  }
}

class DenyEventFilter(val field: Field, predicateEvaluator: PredicateEvaluator) extends EventFilter {
  override def apply(contextWithRequest: ContextWithRequest, event: DynamicRequest)
                    (implicit ec: ExecutionContext): Future[DynamicRequest] = {
    Future {
      DynamicRequest(DynamicBody(DenyFilter.filterBody(field, event.body.content, contextWithRequest, predicateEvaluator)), contextWithRequest.request.headers)
    }
  }
}

object DenyFilter {
  def filterBody(field: Field, body: Value, contextWithRequest: ContextWithRequest, predicateEvaluator: PredicateEvaluator): Value = {
    body match {
      case _: Obj ⇒
        val filteredFields = filterFields(field, body.content.toMap, contextWithRequest, predicateEvaluator)
        Obj(filteredFields)

      case other ⇒
        other
    }
  }

  def filterFields(ramlField: Field, fields: scala.collection.Map[String, Value], contextWithRequest: ContextWithRequest, predicateEvaluator: PredicateEvaluator): scala.collection.Map[String, Value] = {
    if (isPrivateField(ramlField, contextWithRequest, predicateEvaluator))
      erasePrivateField(ramlField.name, fields)
    else
      fields
  }

  def erasePrivateField(pathToField: String, nonPrivateFields: Map[String, Value]): Map[String, Value] = {
    if (pathToField.contains("."))
      pathToField.split(".").toList match {
        case (leadPathSegment :: tailPath :: Nil) ⇒
          nonPrivateFields.get(leadPathSegment) match {
            case Some(subFields) ⇒
              erasePrivateField(tailPath, subFields.toMap)
          }
      }
    else
      nonPrivateFields - pathToField
  }


  def isPrivateField(field: Field, contextWithRequest: ContextWithRequest, predicateEvaluator: PredicateEvaluator): Boolean = {
    field.annotations.find(_.name == RamlAnnotation.DENY) match {
      case Some(DenyAnnotation(_, predicateOpt)) ⇒
        predicateOpt match {
          case Some(predicate) ⇒
            predicateEvaluator.evaluate(predicate, contextWithRequest)
          case None ⇒
            true
        }
      case None ⇒
        false
    }
  }
}
