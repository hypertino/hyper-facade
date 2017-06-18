package com.hypertino.facade.filter.http

import akka.pattern.AskTimeoutException
import com.hypertino.binders.value.{Text, Value}
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.model._
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.model.annotations.{body, request}
import com.hypertino.hyperbus.transport.api.NoTransportRouteException
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.util.IdGenerator
import monix.eval.Task
import monix.execution.{CancelableFuture, Scheduler}
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class AuthenticationRequestFilter(implicit inj: Injector) extends RequestFilter with Injectable {

  val log = LoggerFactory.getLogger(getClass)
  val hyperbus = inject[Hyperbus]
  implicit val scheduler = inject[Scheduler]

  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    implicit val mcx = contextWithRequest.request
    contextWithRequest.originalHeaders.get(FacadeHeaders.AUTHORIZATION) match {
      case Some(Text(credentials)) ⇒
        val authRequest = AuthenticationRequest(AuthenticationRequestBody(credentials))
        val f = hyperbus
          .ask(authRequest)
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
    case hyperbusException: NotFound[ErrorBody] ⇒
      val errorId = IdGenerator.create()
      throw new FilterInterruptException(
        Unauthorized(ErrorBody("unauthorized", errorId = errorId)),
        s"User with credentials ${authRequest.body.credentials} is not authorized!"
      )

    case hyperbusException: InternalServerError[ErrorBody] ⇒
      throw new FilterInterruptException(
        hyperbusException,
        s"Internal error in authorization service"
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

@body("application/vnd.auth+json")
case class AuthenticationRequestBody(credentials: String) extends Body

@body("application/vnd.auth-user+json")
case class AuthenticationResponseBody(authUser: AuthUser) extends Body

@request(Method.GET, "/auth")
case class AuthenticationRequest(body: AuthenticationRequestBody)
  extends Request[Body]
  with DefinedResponse[Ok[AuthenticationResponseBody]]