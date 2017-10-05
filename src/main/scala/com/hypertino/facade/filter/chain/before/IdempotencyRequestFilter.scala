package com.hypertino.facade.filter.chain.before

import com.hypertino.binders.value.{Obj, Text}
import com.hypertino.facade.apiref.idempotency._
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model.{FilterInterruptException, _}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model._
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler

import scala.util.{Failure, Success}

// todo: cover with test
class IdempotencyRequestFilter(hyperbus: Hyperbus,
                               protected val expressionEvaluator: ExpressionEvaluator,
                               protected implicit val scheduler: Scheduler) extends RequestFilter with StrictLogging {

  override def apply(requestContext: RequestContext)
                    (implicit scheduler: Scheduler): Task[RequestContext] = {
    implicit val mcx: MessagingContext = requestContext

    requestContext.originalHeaders.get(IdempotencyHeader.IDEMPOTENCY_KEY) match {
      case Some(Text(idempotencyKey)) ⇒ handleIdempotency(requestContext, idempotencyKey)
      case _ ⇒ Task.now(requestContext)
    }
  }

  private def handleIdempotency(requestContext: RequestContext, idempotencyKey: String)
                               (implicit mcx: MessagingContext): Task[RequestContext] = {
    /*
      1. optimistic lock before request
      2. if request is already locked, then try to get response
      3. if there is response, fire it back
      4. if there is no response, fail the request :(
      5. if request wasn't locked, go ahead with response, preserve information in context, that we need to save a response
         Other part of logic is in the IdempotencyResponseFilter
    */
    val requestInformation = RequestInformation(
      System.currentTimeMillis(),
      requestContext.request.headers.messageId
    )
    val uri = requestContext.request.headers.hrl.location
    hyperbus
      .ask(IdempotentRequestPut(uri, idempotencyKey, requestInformation))
      .materialize
      .flatMap {
        case Success(_) ⇒
          Task.now(requestContext.copy(
            contextStorage = requestContext.contextStorage % Obj.from("idempotent_request" → Obj.from("key" → idempotencyKey, "uri" → uri))
          ))

        case Failure(PreconditionFailed(_, _)) ⇒
          hyperbus
            .ask(IdempotentResponseGet(uri, idempotencyKey))
            .materialize
            .map {
              case Success(ok) ⇒
                val response = StandardResponse(DynamicBody(ok.body.body),
                  MessageHeaders.builder.++=(ok.body.headers.toMap.toSeq).responseHeaders()
                ).asInstanceOf[DynamicResponse]

                Failure(new FilterInterruptException(
                  response, "idempotent response", null
                ))

              case Failure(e) ⇒
                val errorBody = ErrorBody("request-in-progress", Some("Idempotent request is already in progress and no response was saved"))
                logger.warn(s"No response for a request ${requestContext.request} ${errorBody.errorId}", e)
                Failure(Locked(errorBody))
            }
            .dematerialize

        case Failure(exception) ⇒
          Task.raiseError(exception)
      }
  }
}
