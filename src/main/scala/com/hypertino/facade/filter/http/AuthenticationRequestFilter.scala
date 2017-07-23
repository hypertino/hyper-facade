package com.hypertino.facade.filter.http

import com.hypertino.binders.value.{Null, Obj, Text}
import com.hypertino.facade.apiref.auth.{Validation, ValidationsPost}
import com.hypertino.facade.apiref.user.UsersGet
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.PredicateEvaluator
import com.hypertino.facade.model._
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model._
import monix.eval.Task
import monix.execution.Scheduler
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class AuthenticationRequestFilter(hyperbus: Hyperbus,
                                  protected val predicateEvaluator: PredicateEvaluator,
                                  protected implicit val scheduler: Scheduler) extends RequestFilter {
  protected val log = LoggerFactory.getLogger(getClass)

  override def apply(requestContext: RequestContext)
                    (implicit ec: ExecutionContext): Future[RequestContext] = {
    implicit val mcx = requestContext.request
    requestContext.originalHeaders.get(FacadeHeaders.AUTHORIZATION) match {
      case Some(Text(credentials)) ⇒
        getAuthServiceNameFromCredentials(credentials) map { authServiceName ⇒
          val v = ValidationsPost(Validation(credentials))
          val authRequest = v.copy(headers=
            Headers
              .builder
              .++=(v.headers)
              .withHRL(v.headers.hrl.copy(location=authServiceName))
              .requestHeaders()
          )

          hyperbus
            .ask(authRequest)
            .flatMap { validation ⇒
              val userRequest = UsersGet($query=validation.body.identityKeys)
              hyperbus
                .ask(userRequest)
                .flatMap { users ⇒
                  val userCollection = users.body.content.toSeq
                  if (userCollection.isEmpty) {
                    Task.raiseError(Unauthorized(ErrorBody("user-not-exists", Some(s"Credentials are valid, but user doesn't exists"))))
                  }
                  else if (userCollection.tail.nonEmpty) {
                    Task.raiseError(Unauthorized(ErrorBody("multiple-users-found", Some(s"Credentials are valid, but multiple users correspond to the identity keys"))))
                  }
                  else {
                    Task.eval {
                      val user = userCollection.head
                      val userId = user.user_id
                      requestContext.copy(
                        request = requestContext.request.copy(
                          headers = requestContext.request.headers + "Authorization-Result" → Obj.from("user_id" → userId)
                        ),
                        contextStorage = requestContext.contextStorage + Obj.from(
                          ContextStorage.USER → user,
                          ContextStorage.IS_AUTHORIZED → false // this is correct, request is authenticated, but not authorized (yet)
                        )
                      )
                    }
                  }
                }
              //validation.body.
            }
            .runAsync
        } getOrElse {
          Future.failed(
              BadRequest(ErrorBody("unsupported-authorization-scheme", Some(s"Authorization scheme doesn't have first part!")))
          )
        }

      case None ⇒
        Future{
          // Always override "Authorization-Result" to prevent defining from the frontend/outside
          requestContext.copy(
            request = requestContext.request.copy(
              headers = requestContext.request.headers + "Authorization-Result" → Null
            ),
            contextStorage = requestContext.contextStorage + Obj.from(
              ContextStorage.USER → Null,
              ContextStorage.IS_AUTHORIZED → false
            )
          )
        }
    }
  }

  private def getAuthServiceNameFromCredentials(credentials: String): Option[String] = {
    val c = credentials.trim
    val i = c.indexOf(' ')
    if (i > 0) Some {
      ValidationsPost.location.replace("hb://auth/", "hb://auth-" + c.substring(0, i).trim.toLowerCase + "/")
    }
    else {
      None
    }
  }
}
