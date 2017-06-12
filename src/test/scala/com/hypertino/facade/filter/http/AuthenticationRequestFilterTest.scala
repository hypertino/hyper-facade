package com.hypertino.facade.filter.http

import com.hypertino.auth.BasicAuthenticationService
import com.hypertino.authentication.AuthUser
import com.hypertino.binders.value.{Null, Text}
import com.hypertino.facade.TestBase
import com.hypertino.facade.model._
import com.hypertino.facade.modules.TestInjectors
import com.hypertino.facade.raml.Method
import com.hypertino.facade.workers.TestWsRestServiceApp
import com.hypertino.hyperbus.transport.api.uri.Uri
import com.hypertino.servicecontrol.api.Service
import spray.http.BasicHttpCredentials

import scala.concurrent.ExecutionContext.Implicits.global

class AuthenticationRequestFilterTest extends TestBase {

  implicit val injector = TestInjectors()
  inject[BasicAuthenticationService]
  val app = inject[Service].asInstanceOf[TestWsRestServiceApp]
  val filter = new AuthenticationRequestFilter

  "AuthenticationFilter" - {
    "unauthorized: non-existent user" in {
      val unauthorizedRequest = FacadeRequest(
        Uri("/resource"),
        Method.POST,
        Map(FacadeHeaders.AUTHORIZATION → Seq(BasicHttpCredentials("login", "password").toString())),
        Map("field" → Text("value"))
      )
      val requestContext = mockContext(unauthorizedRequest)

      val fail = filter.apply(ContextWithRequest(requestContext, unauthorizedRequest)).failed.futureValue
      fail shouldBe a [FilterInterruptException]

      val response = fail.asInstanceOf[FilterInterruptException].response
      response.status shouldBe 401
    }

    "unauthorized: wrong password" in {
      val unauthorizedRequest = FacadeRequest(
        Uri("/resource"),
        Method.POST,
        Map(FacadeHeaders.AUTHORIZATION → Seq(BasicHttpCredentials("admin", "wrong-password").toString())),
        Map("field" → Text("value"))
      )
      val requestContext = mockContext(unauthorizedRequest)

      val fail = filter.apply(ContextWithRequest(requestContext, unauthorizedRequest)).failed.futureValue
      fail shouldBe a [FilterInterruptException]

      val response = fail.asInstanceOf[FilterInterruptException].response
      response.status shouldBe 401
    }

    "successful" in {
      val request = FacadeRequest(
        Uri("/resource"),
        Method.POST,
        Map(FacadeHeaders.AUTHORIZATION → Seq(BasicHttpCredentials("admin", "admin").toString())),
        Map("field" → Text("value"))
      )
      val requestContext = mockContext(request)

      val filteredRequestContext = filter.apply(ContextWithRequest(requestContext, request)).futureValue.context
      val authUser = filteredRequestContext.contextStorage(ContextStorage.AUTH_USER).asInstanceOf[AuthUser]
      authUser.id shouldBe "1"
      authUser.roles should contain("admin")
      authUser.properties shouldBe Null
    }
  }
}
