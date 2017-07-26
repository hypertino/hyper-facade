package com.hypertino.facade.filter.model

import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.raml.RamlAnnotation
import com.hypertino.hyperbus.model.{DynamicRequest, DynamicResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class ConditionalRequestFilterProxy(annotation: RamlAnnotation, filter: RequestFilter,
                                         protected val expressionEvaluator: ExpressionEvaluator) extends RequestFilter {
  override def apply(contextWithRequest: RequestContext)
                    (implicit ec: ExecutionContext): Future[RequestContext] = {
    annotation.predicate match {
      case Some(p) ⇒
        Try(filter.evaluatePredicate(contextWithRequest, p)) match {
          case Success(true) ⇒
            filter.apply(contextWithRequest)
          case Success(false) ⇒
            Future(contextWithRequest)
          case Failure(ex) ⇒
            Future.failed(ex)
        }
      case None ⇒
        filter.apply(contextWithRequest)
    }
  }
}

case class ConditionalResponseFilterProxy(annotation: RamlAnnotation, filter: ResponseFilter,
                                          protected val expressionEvaluator: ExpressionEvaluator) extends ResponseFilter {
  override def apply(contextWithRequest: RequestContext, response: DynamicResponse)
                    (implicit ec: ExecutionContext): Future[DynamicResponse] = {
    annotation.predicate match {
      case Some(p) ⇒
        Try(filter.evaluatePredicate(contextWithRequest, p)) match {
          case Success(true) ⇒
            filter.apply(contextWithRequest, response)
          case Success(false) ⇒
            Future(response)
          case Failure(ex) ⇒
            Future.failed(ex)
        }

      case None ⇒
        filter.apply(contextWithRequest, response)
    }
  }
}

case class ConditionalEventFilterProxy(annotation: RamlAnnotation, filter: EventFilter,
                                       protected val expressionEvaluator: ExpressionEvaluator) extends EventFilter {
  override def apply(contextWithRequest: RequestContext, event: DynamicRequest)
                    (implicit ec: ExecutionContext): Future[DynamicRequest] = {
    annotation.predicate match {
      case Some(p) ⇒
        Try(filter.evaluatePredicate(contextWithRequest, p)) match {
          case Success(true) ⇒
            filter.apply(contextWithRequest, event)
          case Success(false) ⇒
            Future(event)
          case Failure(ex) ⇒
            Future.failed(ex)
        }

      case None ⇒
        filter.apply(contextWithRequest, event)
    }
  }
}
