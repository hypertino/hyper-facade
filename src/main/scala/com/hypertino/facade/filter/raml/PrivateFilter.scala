package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Obj, Value}
import com.hypertino.facade.filter.model.{EventFilter, Filter, ResponseFilter}
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.raml.{Field, PrivateAnnotation, RamlAnnotation}
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicResponse, StandardResponse}
import com.hypertino.parser.HParser
import com.hypertino.parser.ast.Identifier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class PrivateResponseFilter(protected val field: Field, protected val expressionEvaluator: ExpressionEvaluator) extends ResponseFilter with PrivateFilter {
  override def apply(contextWithRequest: RequestContext, response: DynamicResponse)
                    (implicit ec: ExecutionContext): Future[DynamicResponse] = {
    Future {
      StandardResponse(body = DynamicBody(filterBody(response.body.content, contextWithRequest)), response.headers)
        .asInstanceOf[DynamicResponse]
    }
  }
}

class PrivateEventFilter(protected val field: Field, protected val expressionEvaluator: ExpressionEvaluator) extends EventFilter with PrivateFilter {
  override def apply(contextWithRequest: RequestContext, event: DynamicRequest)
                    (implicit ec: ExecutionContext): Future[DynamicRequest] = {
    Future {
      DynamicRequest(DynamicBody(filterBody(event.body.content, contextWithRequest)), contextWithRequest.request.headers)
    }
  }
}

trait PrivateFilter extends Filter {
  protected  def field: Field
  protected val fieldSegments = HParser(field.name) match {
    case Identifier(segments) ⇒ segments
    case other ⇒ Seq(field.name)
  }

  protected def filterBody(body: Value, contextWithRequest: RequestContext): Value = {
    body match {
      case obj: Obj if applyPrivateFilter(contextWithRequest) ⇒
        removeField(fieldSegments, obj)

      case other ⇒
        other
    }
  }

  protected def applyPrivateFilter(contextWithRequest: RequestContext): Boolean = {
    field.annotations.find(_.name == RamlAnnotation.PRIVATE) match {
      case Some(PrivateAnnotation(_, predicateOpt)) ⇒
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

  def removeField(path: Seq[String], o: Obj): Obj = {
    if (path.nonEmpty && path.tail.isEmpty) {
      Obj(o.v.filterNot(_._1 == path.head))
    } else {
      if (path.isEmpty) {
        o
      }
      else {
        Obj(o.v.map {
          case (k, v: Obj) if k == path.head ⇒ k → removeField(path.tail,v)
          case el ⇒ el
        })
      }
    }
  }
}
