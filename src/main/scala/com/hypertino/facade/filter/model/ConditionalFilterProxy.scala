package com.hypertino.facade.filter.model

import com.hypertino.facade.filter.parser.PredicateEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.raml.RamlAnnotation
import com.hypertino.hyperbus.model.{DynamicRequest, DynamicResponse}

import scala.concurrent.{ExecutionContext, Future}

case class ConditionalRequestFilterProxy(annotation: RamlAnnotation, filter: RequestFilter, predicateEvaluator: PredicateEvaluator) extends RequestFilter {
  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    annotation.predicate match {
      case Some(p) ⇒
        if (predicateEvaluator.evaluate(p, contextWithRequest))
          filter.apply(contextWithRequest)
        else
          Future(contextWithRequest)

      case None ⇒
        filter.apply(contextWithRequest)
    }
  }
}

case class ConditionalResponseFilterProxy(annotation: RamlAnnotation, filter: ResponseFilter, predicateEvaluator: PredicateEvaluator) extends ResponseFilter {
  override def apply(contextWithRequest: ContextWithRequest, response: DynamicResponse)
                    (implicit ec: ExecutionContext): Future[DynamicResponse] = {
    annotation.predicate match {
      case Some(p) ⇒
        if (predicateEvaluator.evaluate(p, contextWithRequest))
          filter.apply(contextWithRequest, response)
        else
          Future(response)

      case None ⇒
        filter.apply(contextWithRequest, response)
    }
  }
}

case class ConditionalEventFilterProxy(annotation: RamlAnnotation, filter: EventFilter, predicateEvaluator: PredicateEvaluator) extends EventFilter {
  override def apply(contextWithRequest: ContextWithRequest, event: DynamicRequest)
                    (implicit ec: ExecutionContext): Future[DynamicRequest] = {
    annotation.predicate match {
      case Some(p) ⇒
        if (predicateEvaluator.evaluate(p, contextWithRequest))
          filter.apply(contextWithRequest, event)
        else
          Future(event)

      case None ⇒
        filter.apply(contextWithRequest, event)
    }
  }
}
