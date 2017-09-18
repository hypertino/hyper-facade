package com.hypertino.facade.filter.chain.after

import com.hypertino.binders.value.Obj
import com.hypertino.facade.apiref.idempotency.{IdempotentResponsePut, ResponseWrapper}
import com.hypertino.facade.filter.model.ResponseFilter
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model.RequestContext
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{DynamicResponse, MessagingContext}
import com.typesafe.scalalogging.StrictLogging
import monix.execution.Scheduler

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

// todo: cover with test
class IdempotencyResponseFilter(hyperbus: Hyperbus,
                                protected val expressionEvaluator: ExpressionEvaluator,
                                protected implicit val scheduler: Scheduler) extends ResponseFilter with StrictLogging {

  override def apply(contextWithRequest: RequestContext, response: DynamicResponse)
                    (implicit ec: ExecutionContext): Future[DynamicResponse] = {

    implicit val mcx: MessagingContext = contextWithRequest
    contextWithRequest.contextStorage.idempotent_request match {
      case o: Obj ⇒ saveIdempotentResponse(o.uri.toString, o.key.toString, response)
      case _ ⇒ Future(response)
    }
  }

  private def saveIdempotentResponse(uri: String, key: String, response: DynamicResponse)
                                    (implicit mcx: MessagingContext): Future[DynamicResponse] = {
    val responseWrapper = ResponseWrapper(Obj(response.headers.underlying), response.body.content)
    hyperbus
      .ask(IdempotentResponsePut(uri,key,responseWrapper))
      .onErrorRestart(3)
      .materialize
      .map {
        case Success(_) ⇒
          response

        case Failure(e) ⇒
          logger.error(s"Failed to save idempotent response for $uri/$key, this may lead to locked resource", e)
          response
      }
      .runAsync
  }
}