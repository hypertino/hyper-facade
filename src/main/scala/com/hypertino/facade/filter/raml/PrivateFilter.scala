package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Obj, Value}
import com.hypertino.facade.filter.model.{EventFilter, Filter, ResponseFilter}
import com.hypertino.facade.filter.parser.PredicateEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.raml.{DenyAnnotation, Field, RamlAnnotation}
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicResponse, StandardResponse}
import scaldi.Injector

import scala.collection.Map
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class PrivateResponseFilter(field: Field, protected val predicateEvaluator: PredicateEvaluator) extends ResponseFilter with PrivateFilter {

  override def apply(contextWithRequest: ContextWithRequest, response: DynamicResponse)
                    (implicit ec: ExecutionContext): Future[DynamicResponse] = {
    Future {
      StandardResponse(body = DynamicBody(filterBody(field, response.body.content, contextWithRequest)), response.headers)
        .asInstanceOf[DynamicResponse]
    }
  }
}

class PrivateEventFilter(field: Field, protected val predicateEvaluator: PredicateEvaluator) extends EventFilter with PrivateFilter {
  override def apply(contextWithRequest: ContextWithRequest, event: DynamicRequest)
                    (implicit ec: ExecutionContext): Future[DynamicRequest] = {
    Future {
      DynamicRequest(DynamicBody(filterBody(field, event.body.content, contextWithRequest)), contextWithRequest.request.headers)
    }
  }
}

trait PrivateFilter extends Filter {
  protected def filterBody(field: Field, body: Value, contextWithRequest: ContextWithRequest): Value = {
    body match {
      case _: Obj ⇒
        val filteredFields = filterFields(field, body.content.toMap, contextWithRequest)
        Obj(filteredFields)

      case other ⇒
        other
    }
  }

  protected def filterFields(ramlField: Field, fields: scala.collection.Map[String, Value], contextWithRequest: ContextWithRequest): scala.collection.Map[String, Value] = {
    if (isPrivateField(ramlField, contextWithRequest))
      erasePrivateField(ramlField.name, fields)
    else
      fields
  }

  protected def erasePrivateField(pathToField: String, nonPrivateFields: Map[String, Value]): Map[String, Value] = {
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


  protected def isPrivateField(field: Field, contextWithRequest: ContextWithRequest): Boolean = {
    field.annotations.find(_.name == RamlAnnotation.DENY) match {
      case Some(DenyAnnotation(_, predicateOpt)) ⇒
        predicateOpt match {
          case Some(predicate) ⇒
            Try(evaluatePredicate(contextWithRequest, predicate)) match {
              case Success(true) | Failure(_) ⇒ true
              case Success(false) ⇒ false
            }
          case None ⇒
            true
        }
      case None ⇒
        false
    }
  }
}
