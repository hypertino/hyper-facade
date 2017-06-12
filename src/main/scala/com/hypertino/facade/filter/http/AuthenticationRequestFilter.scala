package com.hypertino.facade.filter.http

import akka.pattern.AskTimeoutException
import com.hypertino.authentication.AuthUser
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.model._
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.model.annotations.{body, request}
import com.hypertino.hyperbus.transport.api.NoTransportRouteException
import com.hypertino.hyperbus.{Hyperbus, IdGenerator}
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class AuthenticationRequestFilter(implicit inj: Injector) extends RequestFilter with Injectable {

  val log = LoggerFactory.getLogger(getClass)
  val hyperbus = inject[Hyperbus]

  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    val context = contextWithRequest.context
    context.requestHeaders.get(FacadeHeaders.AUTHORIZATION) match {
      case Some(credentials :: _) ⇒
        val authRequest = AuthenticationRequest(AuthenticationRequestBody(credentials))
        hyperbus <~ authRequest map { response ⇒
          val updatedContextStorage = context.contextStorage + (ContextStorage.AUTH_USER → response.body.authUser)
          contextWithRequest.copy(
            context = context.copy(
              contextStorage = updatedContextStorage
            )
          )
        } recover {
          handleHyperbusExceptions(authRequest)
        }

      case None ⇒
        Future(contextWithRequest)
    }
  }

  def handleHyperbusExceptions(authRequest: AuthenticationRequest): PartialFunction[Throwable, ContextWithRequest] = {
    case hyperbusException: NotFound[ErrorBody] ⇒
      val errorId = IdGenerator.create()
      throw new FilterInterruptException(
        FacadeResponse(Unauthorized(ErrorBody("unauthorized", errorId = errorId))),
        s"User with credentials ${authRequest.body.credentials} is not authorized!"
      )

    case hyperbusException: InternalServerError[ErrorBody] ⇒
      throw new FilterInterruptException(
        FacadeResponse(hyperbusException),
        s"Internal error in authorization service"
      )

    case noRoute: NoTransportRouteException ⇒
      val errorId = IdGenerator.create()
      throw new FilterInterruptException(
        FacadeResponse(NotFound(ErrorBody("not-found", Some(s"${authRequest.uri} is not found."), errorId = errorId))),
        s"Service ${authRequest.uri} is not found"
      )

    case askTimeout: AskTimeoutException ⇒
      val errorId = IdGenerator.create()
      log.error(s"Timeout #$errorId while handling $authRequest")
      throw new FilterInterruptException(
        FacadeResponse(GatewayTimeout(ErrorBody("service-timeout", Some(s"Timeout while serving '${authRequest.uri}'"), errorId = errorId))),
        s"Timeout while handling $authRequest"
      )

    case other: Throwable ⇒
      val errorId = IdGenerator.create()
      log.error(s"error $errorId", other)
      throw new FilterInterruptException(
        FacadeResponse(InternalServerError(ErrorBody("internal-error", errorId = errorId))),
        "Internal error"
      )
  }
}

@body("application/vnd.auth+json")
case class AuthenticationRequestBody(credentials: String) extends Body

@body("application/vnd.auth-user+json")
case class AuthenticationResponseBody(authUser: AuthUser) extends Body

@request(Method.GET, "/auth")
case class AuthenticationRequest(body: AuthenticationRequestBody)
  extends Request[Body]
  with DefinedResponse[Ok[AuthenticationResponseBody]]