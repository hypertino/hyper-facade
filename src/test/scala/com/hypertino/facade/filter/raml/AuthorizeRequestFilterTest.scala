package com.hypertino.facade.filter.raml

import com.hypertino.facade.FacadeConfigPaths
import com.hypertino.authentication.AuthUser
import com.hypertino.binders.value.{Null, Text}
import com.hypertino.facade.filter.chain.FilterChain
import com.hypertino.facade.model.ContextStorage.ExtendFacadeRequestContext
import com.hypertino.facade.model._
import com.hypertino.facade.modules.TestInjectors
import com.hypertino.facade.workers.TestWsRestServiceApp
import com.hypertino.facade.TestBase
import com.hypertino.hyperbus.model.Method
import com.hypertino.hyperbus.transport.api.uri.Uri
import com.hypertino.servicecontrol.api.Service

import scala.concurrent.ExecutionContext.Implicits.global

class AuthorizeRequestFilterTest extends TestBase {
  System.setProperty(FacadeConfigPaths.RAML_FILE, "raml-configs/auth-request-filter-test.raml")
  implicit val injector = TestInjectors()
  val ramlFilters = inject[FilterChain]("ramlFilterChain")
  val app = inject[Service].asInstanceOf[TestWsRestServiceApp]

  "AuthorizeRequestFilterTest" - {
    "resource. not authorized" in {
      val unauthorizedRequest = FacadeRequest(
        Uri("/auth-resource"),
        Method.GET,
        Map.empty,
        Map("field" → Text("value"))
      )
      val cwr = ContextWithRequest(mockContext(unauthorizedRequest), unauthorizedRequest)
      val filteredCtxWithRequest = ramlFilters.filterRequest(cwr).futureValue
      filteredCtxWithRequest.context.isAuthorized shouldBe false
    }

    "resource. authorized" in {
      val authorizedRequest = FacadeRequest(
        Uri("/auth-resource"),
        Method.GET,
        Map.empty,
        Map("field" → Text("value"))
      )
      val ctx = mockContext(authorizedRequest)
      val updatedCtxStorage = ctx.contextStorage + (ContextStorage.AUTH_USER → AuthUser("123456", Set.empty, Null))
      val cwr = ContextWithRequest(ctx.copy(contextStorage = updatedCtxStorage), authorizedRequest)
      val filteredCtxWithRequest = ramlFilters.filterRequest(cwr).futureValue
      filteredCtxWithRequest.context.isAuthorized shouldBe true
    }

    "method. not authorized" in {
      val unauthorizedRequest = FacadeRequest(
        Uri("/auth-resource"),
        Method.POST,
        Map.empty,
        Map("field" → Text("value"))
      )
      val cwr = ContextWithRequest(mockContext(unauthorizedRequest), unauthorizedRequest)
      val filteredCtxWithRequest = ramlFilters.filterRequest(cwr).futureValue
      filteredCtxWithRequest.context.isAuthorized shouldBe false
    }

    "method. authorized" in {
      val authorizedRequest = FacadeRequest(
        Uri("/auth-resource"),
        Method.POST,
        Map.empty,
        Map("field" → Text("value"))
      )
      val ctx = mockContext(authorizedRequest)
      val updatedCtxStorage = ctx.contextStorage + (ContextStorage.AUTH_USER → AuthUser("123456", Set.empty, Null))
      val cwr = ContextWithRequest(ctx.copy(contextStorage = updatedCtxStorage), authorizedRequest)
      val filteredCtxWithRequest = ramlFilters.filterRequest(cwr).futureValue
      filteredCtxWithRequest.context.isAuthorized shouldBe true
    }
  }
}
