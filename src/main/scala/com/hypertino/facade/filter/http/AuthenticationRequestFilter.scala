package com.hypertino.facade.filter.http

import akka.pattern.AskTimeoutException
import com.hypertino.binders.value.{Text, Value}
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.PredicateEvaluator
import com.hypertino.facade.model._
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.model.annotations.{body, request}
import com.hypertino.hyperbus.transport.api.NoTransportRouteException
import com.hypertino.hyperbus.util.IdGenerator
import monix.execution.Scheduler
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class AuthenticationRequestFilter(hyperbus: Hyperbus,
                                  protected val predicateEvaluator: PredicateEvaluator,
                                  protected implicit val scheduler: Scheduler) extends RequestFilter {
  protected val log = LoggerFactory.getLogger(getClass)

  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    implicit val mcx = contextWithRequest.request
    contextWithRequest.originalHeaders.get(FacadeHeaders.AUTHORIZATION) match {
      case Some(Text(credentials)) ⇒
        val authRequest = AuthenticationRequest(AuthenticationRequestBody(credentials))
        val f = hyperbus
          .ask(authRequest)(AuthenticationRequest.meta) // todo: why implicit isn't found?
          .runAsync(scheduler)

        f.map { case response: Ok[AuthenticationResponseBody] ⇒
            val updatedContextStorage = contextWithRequest.contextStorage + (ContextStorage.AUTH_USER → response.body.authUser)
            contextWithRequest.copy(
              contextStorage = updatedContextStorage
            )
          } recover handleHyperbusExceptions(authRequest)

      case None ⇒
        Future(contextWithRequest)
    }
  }

  def handleHyperbusExceptions(implicit authRequest: AuthenticationRequest): PartialFunction[Throwable, ContextWithRequest] = {
    case _: NotFound[ErrorBody] ⇒
      val errorId = IdGenerator.create()
      throw new FilterInterruptException(
        Unauthorized(ErrorBody("unauthorized", errorId = errorId)),
        s"User with credentials ${authRequest.body.credentials} is not authorized!"
      )

    case hyperbusException: HyperbusError[ErrorBody] ⇒
      throw new FilterInterruptException(
        hyperbusException,
        s"Unhandled exception in authorization service"
      )

    case noRoute: NoTransportRouteException ⇒
      val errorId = IdGenerator.create()
      throw new FilterInterruptException(
       NotFound(ErrorBody("not-found", Some(s"${authRequest.headers.hrl} is not found."), errorId = errorId)),
        s"Resource ${authRequest.headers.hrl} is not found"
      )

    case askTimeout: AskTimeoutException ⇒
      val errorId = IdGenerator.create()
      log.error(s"Timeout #$errorId while handling $authRequest")
      throw new FilterInterruptException(
        GatewayTimeout(ErrorBody("service-timeout", Some(s"Timeout while serving '${authRequest.headers.hrl}'"), errorId = errorId)),
        s"Timeout while handling $authRequest"
      )

    case other: Throwable ⇒
      val errorId = IdGenerator.create()
      log.error(s"error $errorId", other)
      throw new FilterInterruptException(
        InternalServerError(ErrorBody("internal-error", errorId = errorId)),
        "Internal error"
      )
  }
}

case class AuthUser(id: String, roles: Seq[String], extra: Value)

@body("auth")
case class AuthenticationRequestBody(credentials: String) extends Body

@body("auth-user")
case class AuthenticationResponseBody(authUser: AuthUser) extends Body

@request(Method.GET, "hb://auth")
case class AuthenticationRequest(body: AuthenticationRequestBody)
  extends Request[AuthenticationRequestBody]
  with DefinedResponse[Ok[AuthenticationResponseBody]]

object AuthenticationRequest extends com.hypertino.hyperbus.model.RequestMetaCompanion[AuthenticationRequest]{
  implicit val meta = this
  type ResponseType = Ok[AuthenticationResponseBody]
}
