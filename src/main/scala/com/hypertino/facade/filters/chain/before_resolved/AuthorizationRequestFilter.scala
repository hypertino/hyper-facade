/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.chain.before_resolved

import com.hypertino.binders.value.{Null, Obj, Text, Value}
import com.hypertino.facade.apiref.auth.{AuthHeader, Validation, ValidationResult, ValidationsPost}
import com.hypertino.facade.apiref.user.UsersGet
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, ExpressionEvaluatorContext}
import com.hypertino.facade.filters.annotated.AuthorizeAnnotation
import com.hypertino.facade.model._
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model._
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler

private[before_resolved] case class TaskResult(headerName: String, headerValue: Value, contextValue: Value)

class AuthorizationRequestFilter(hyperbus: Hyperbus,
                                 protected val expressionEvaluator: ExpressionEvaluator,
                                 protected implicit val scheduler: Scheduler,
                                 protected val authorizeAnnotation: Option[AuthorizeAnnotation] = None
                                ) extends RequestFilter with StrictLogging {

  override def apply(requestContext: RequestContext)
                    (implicit scheduler: Scheduler): Task[RequestContext] = {
    implicit val mcx = requestContext.request

    Task.gatherUnordered(Seq(authorizationTask(requestContext), privelegeAuthorizationTask(requestContext))).map { results ⇒
      val removeHeaders = results.filter(_.headerValue.isEmpty).map(_.headerName)
      val addHeaders = RequestHeaders(Headers(
        results.filter(_.headerValue.isDefined).map(t ⇒ t.headerName → t.headerValue) :_*
      ))
      val contextObj = results.foldLeft[Value](Null) { (current: Value, t) ⇒
        current + t.contextValue
      }

      requestContext.copy(
        request = requestContext.request.copy(
          headers = RequestHeaders(Headers(requestContext
            .request
            .headers
            .toSeq
            .filterNot(_._1 == removeHeaders) ++ addHeaders :_*))
        ),
        contextStorage = requestContext.contextStorage + contextObj
      )
    }
  }

  private def validateCredentials(credentials: String)(implicit mcx: MessagingContext): Task[ValidationResult]= {
    getAuthServiceNameFromCredentials(credentials) map { authServiceName ⇒
      val v = ValidationsPost(Validation(credentials))
      val authRequest = v.copy(headers =
        MessageHeaders
          .builder
          .++=(v.headers)
          .withHRL(v.headers.hrl.copy(location = authServiceName))
          .requestHeaders()
      )

      hyperbus.ask(authRequest).map(_.body)
    } getOrElse {
      Task.raiseError(
        BadRequest(ErrorBody("unsupported-authorization-scheme", Some(s"Authorization scheme doesn't have first part!")))
      )
    }
  }

  private def authorizationTask(implicit requestContext: RequestContext): Task[TaskResult] = {
    authorizationHeader(requestContext, AuthorizeAnnotation.MODE_NORMAL) match {
      case Some(Text(credentials)) ⇒
        validateCredentials(credentials)
          .flatMap { validation ⇒
            val userRequest = UsersGet(query = validation.identityKeys)
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
                    val userId = user.dynamic.user_id
                    TaskResult(AuthHeader.AUTHORIZATION_RESULT, Obj.from("user_id" → userId), Obj.from(
                      ContextStorage.USER → user
                    ))
                  }
                }
              }
            //validation.body.
          }

      case _ ⇒
        Task.now(TaskResult(AuthHeader.AUTHORIZATION_RESULT, Null, Null))
    }
  }

  private def authorizationHeader(requestContext: RequestContext, mode: String): Option[Value] = {
    authorizeAnnotation.filter(_.mode.contains(mode)).map { aa ⇒
      val ctx = ExpressionEvaluatorContext(requestContext, Obj.empty)
      expressionEvaluator.evaluate(ctx, aa.source)
    } orElse {
      if (mode == AuthorizeAnnotation.MODE_PRIVILEGE) {
        requestContext.httpHeaders.get(FacadeHeaders.PRIVILEGE_AUTHORIZATION)
      }
      else {
        requestContext.httpHeaders.get(FacadeHeaders.AUTHORIZATION)
      }
    }
  }

  private def privelegeAuthorizationTask(implicit requestContext: RequestContext): Task[TaskResult] = {
    authorizationHeader(requestContext, AuthorizeAnnotation.MODE_PRIVILEGE) match {
      case Some(Text(credentials)) ⇒
        validateCredentials(credentials).map { v ⇒
          TaskResult(AuthHeader.PRIVILEGE_AUTHORIZATION_RESULT, Obj.from(
            "identity_keys" → v.identityKeys,
            "extra" → v.extra
          ), Null)
        }
      case _ ⇒
        Task.now(TaskResult(AuthHeader.PRIVILEGE_AUTHORIZATION_RESULT, Null, Null))
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
