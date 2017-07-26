package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.Obj
import com.hypertino.facade.filter.chain.{FilterChain, SimpleFilterChain}
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model.{ContextStorage, RequestContext}
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class AuthorizeRequestFilter(protected val expressionEvaluator: ExpressionEvaluator) extends RequestFilter {

  override def apply(contextWithRequest: RequestContext)
                    (implicit ec: ExecutionContext): Future[RequestContext] = {
    Future {
      val updatedContextStorage = contextWithRequest.contextStorage + Obj.from(ContextStorage.IS_AUTHORIZED → true)
      contextWithRequest.copy (
        contextStorage = updatedContextStorage
      )
    }
  }
}

class AuthorizeFilterFactory(protected val predicateEvaluator: ExpressionEvaluator) extends RamlFilterFactory {
  private val log = LoggerFactory.getLogger(getClass)

  override def createFilters(target: RamlTarget): SimpleFilterChain = {
    target match {
      case TargetResource(_, _) ⇒
        SimpleFilterChain(
          requestFilters = Seq(new AuthorizeRequestFilter(predicateEvaluator)),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )

      case TargetMethod(_, _, _) ⇒
        SimpleFilterChain(
          requestFilters = Seq(new AuthorizeRequestFilter(predicateEvaluator)),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )

      case unknownTarget ⇒
        log.warn(s"Annotation (authorize) is not supported for target $unknownTarget. Empty filter chain will be created")
        FilterChain.empty
    }
  }
}