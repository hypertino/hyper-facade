package com.hypertino.facade.filter.model

import com.hypertino.binders.value.{Null, Obj}
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, ExpressionEvaluatorContext}
import com.hypertino.facade.model._
import com.hypertino.facade.raml.RamlAnnotation
import com.hypertino.hyperbus.model.{DynamicRequest, DynamicResponse}
import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class ConditionalRequestFilterProxy(annotation: RamlAnnotation, filter: RequestFilter,
                                         protected val expressionEvaluator: ExpressionEvaluator) extends RequestFilter {
  override def apply(requestContext: RequestContext)
                    (implicit scheduler: Scheduler): Task[RequestContext] = {
    annotation.predicate match {
      case Some(p) ⇒
        Try(filter.evaluatePredicate(ExpressionEvaluatorContext(requestContext, Obj.empty), p)) match {
          case Success(true) ⇒
            filter.apply(requestContext)
          case Success(false) ⇒
            Task.now(requestContext)
          case Failure(ex) ⇒
            Task.raiseError(ex)
        }
      case None ⇒
        filter.apply(requestContext)
    }
  }
}

case class ConditionalResponseFilterProxy(annotation: RamlAnnotation, filter: ResponseFilter,
                                          protected val expressionEvaluator: ExpressionEvaluator) extends ResponseFilter {
  override def apply(requestContext: RequestContext, response: DynamicResponse)
                    (implicit scheduler: Scheduler): Task[DynamicResponse] = {
    annotation.predicate match {
      case Some(p) ⇒
        Try(filter.evaluatePredicate(ExpressionEvaluatorContext(requestContext, Obj.empty), p)) match {
          case Success(true) ⇒
            filter.apply(requestContext, response)
          case Success(false) ⇒
            Task.now(response)
          case Failure(ex) ⇒
            Task.raiseError(ex)
        }

      case None ⇒
        filter.apply(requestContext, response)
    }
  }
}

case class ConditionalEventFilterProxy(annotation: RamlAnnotation, filter: EventFilter,
                                       protected val expressionEvaluator: ExpressionEvaluator) extends EventFilter {
  override def apply(requestContext: RequestContext, event: DynamicRequest)
                    (implicit scheduler: Scheduler): Task[DynamicRequest] = {
    annotation.predicate match {
      case Some(p) ⇒
        Try(filter.evaluatePredicate(ExpressionEvaluatorContext(requestContext, Obj.empty), p)) match {
          case Success(true) ⇒
            filter.apply(requestContext, event)
          case Success(false) ⇒
            Task.now(event)
          case Failure(ex) ⇒
            Task.raiseError(ex)
        }

      case None ⇒
        filter.apply(requestContext, event)
    }
  }
}
