package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{ObjV, Text}
import com.hypertino.facade.model.{ContextWithRequest, FacadeRequest}
import com.hypertino.facade.raml._
import com.hypertino.facade.{CleanRewriteIndex, MockContext}
import com.hypertino.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global

class RewriteRequestFilterTest extends FreeSpec with Matchers with ScalaFutures with CleanRewriteIndex with MockContext {

  override def beforeAll() = {
    RewriteIndexHolder.updateRewriteIndex("/test-rewrite", "/rewritten", None)
    RewriteIndexHolder.updateRewriteIndex("/rewritten", "/rewritten-twice", None)
  }

  "RewriteRequestFilter" - {
    "simple rewrite" in {
      val filter = new RewriteRequestFilter("/rewritten/some-service")
      val request = FacadeRequest(
        Uri("/test-rewrite/some-service"),
        Method.GET,
        Map.empty,
        ObjV("field" → "value")
      )

      val requestContext = mockContext(request)
      val filteredRequest = filter.apply(ContextWithRequest(requestContext, request)).futureValue.request

      val expectedRequest = FacadeRequest(
        Uri("/rewritten/some-service"),
        Method.GET,
        Map.empty,
        Map("field" → Text("value")))

      filteredRequest shouldBe expectedRequest
    }

    "rewrite with arguments" in {
      val filter = new RewriteRequestFilter("/test-rewrite/some-service/{serviceId}")
      val request = FacadeRequest(
        Uri("/rewritten/some-service/{serviceId}", Map("serviceId" → "100500")),
        Method.GET,
        Map.empty,
        ObjV("field" → "value")
      )

      val requestContext = mockContext(request)
      val filteredRequest = filter.apply(ContextWithRequest(requestContext, request)).futureValue.request

      val expectedRequest = FacadeRequest(
        Uri("/test-rewrite/some-service/{serviceId}", Map("serviceId" → "100500")),
        Method.GET,
        Map.empty,
        ObjV("field" → "value")
      )

      filteredRequest shouldBe expectedRequest
    }

    "rewrite links" in {
      val filter = new RewriteRequestFilter("/rewritten/some-service")
      val request = FacadeRequest(
        Uri("/test-rewrite/some-service"), Method.POST, Map.empty, ObjV("field" → "content")
      )
      val requestContext = mockContext(request)

      val filteredRequest = filter.apply(ContextWithRequest(requestContext, request)).futureValue.request
      val expectedRequest = FacadeRequest(
        Uri("/rewritten/some-service"), Method.POST, Map.empty, ObjV("field" → "content"))

      filteredRequest shouldBe expectedRequest
    }
  }
}
