package com.hypertino.facade.filter.chain

import com.hypertino.facade.FacadeConfigPaths
import com.hypertino.binders.value.{Null, ObjV}
import com.hypertino.facade.filter.NoOpFilter
import com.hypertino.facade.filter.model.{ConditionalEventFilterProxy, ConditionalRequestFilterProxy, ConditionalResponseFilterProxy}
import com.hypertino.facade.filter.raml._
import com.hypertino.facade.model.{FacadeRequest, _}
import com.hypertino.facade.modules.TestInjectors
import com.hypertino.facade.raml.{EnrichAnnotation, RamlAnnotation}
import com.hypertino.facade.workers.TestWsRestServiceApp
import com.hypertino.facade.TestBase
import com.hypertino.hyperbus.transport.api.uri.Uri
import com.hypertino.servicecontrol.api.Service

// todo: important to test when specific != formatted!
// + integrated test with filter lookup when specific != formatted!

class RamlFilterChainTest extends TestBase {
  System.setProperty(FacadeConfigPaths.RAML_FILE, "raml-configs/raml-filter-chain-test.raml")
  implicit val injector = TestInjectors()
  val filterChain = inject [FilterChain].asInstanceOf[RamlFilterChain]
  val app = inject[Service].asInstanceOf[TestWsRestServiceApp]

  "FilterChainRamlFactory " - {
    "resource filter chain" in {
      val request = FacadeRequest(Uri("/private"), "get", Map.empty, Null)
      val context = mockContext(request)
      val filters = filterChain.findRequestFilters(ContextWithRequest(context, request))
      filters.length should equal(1)
      filters.head.asInstanceOf[ConditionalRequestFilterProxy].filter shouldBe a[DenyRequestFilter]
    }

    "annotation based filter chain" in {
      val request = FacadeRequest(Uri("/status/test-service"), "get", Map.empty, Null)
      val context = mockContext(request)
      val filters = filterChain.findRequestFilters(ContextWithRequest(context, request))
      filters.length should equal(2)
      val firstFilter = filters.head.asInstanceOf[ConditionalRequestFilterProxy]
      val secondFilter = filters.last.asInstanceOf[ConditionalRequestFilterProxy]

      firstFilter.filter shouldBe a[EnrichRequestFilter]
      val firstAnnotation = firstFilter.annotation
      firstAnnotation shouldBe a[EnrichAnnotation]
      firstAnnotation.name shouldBe RamlAnnotation.CLIENT_IP

      secondFilter.filter shouldBe a[EnrichRequestFilter]
      val secondAnnotation = secondFilter.annotation
      secondAnnotation shouldBe a[EnrichAnnotation]
      secondAnnotation.name shouldBe RamlAnnotation.CLIENT_LANGUAGE
    }

    "trait and annotation based filter chain" in {
      val request = FacadeRequest(Uri("/users/{userId}", Map("userId" → "100500")), "get", Map.empty, Null)
      val response = FacadeResponse(200, Map.empty, Null)
      val filters = filterChain.findResponseFilters(mockContext(request), response)

      filters.head.asInstanceOf[ConditionalResponseFilterProxy].filter shouldBe a[NoOpFilter]
      filters.tail.head.asInstanceOf[ConditionalResponseFilterProxy].filter shouldBe a[DenyResponseFilter]
    }

    "event filter chain (annotation fields)" in {
      val request = FacadeRequest(Uri("/users/{userId}", Map("userId" → "100500")), "get", Map.empty, Null)
      val event = FacadeRequest(request.uri, "feed:put", Map.empty,
        ObjV("fullName" → "John Smith", "userName" → "jsmith", "password" → "neverforget")
      )
      val filters = filterChain.findEventFilters(mockContext(request), event)
      filters.head.asInstanceOf[ConditionalEventFilterProxy].filter shouldBe a[DenyEventFilter]
      filters.length should equal(1)
    }

    "rewrite filters. forward request filters, inverted event filters" in {
      val request = FacadeRequest(Uri("/original-resource"), "get", Map.empty, Null)
      val context = mockContext(request.copy(uri=Uri(request.uri.formatted)))
      val event = FacadeRequest(Uri("/rewritten-resource"), "feed:put", Map.empty,
        ObjV("fullName" → "John Smith", "userName" → "jsmith", "password" → "neverforget")
      )
      val requestFilters = filterChain.findRequestFilters(ContextWithRequest(context, request))
      val eventFilters = filterChain.findEventFilters(context, event)

      requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter shouldBe a[RewriteRequestFilter]
      eventFilters.head.asInstanceOf[ConditionalEventFilterProxy].filter shouldBe a[RewriteEventFilter]
    }

    "rewrite filters with args. forward request filters, inverted event filters" in {
      val request = FacadeRequest(Uri("/original-resource/{arg}", Map("arg" → "100500")), "get", Map.empty, Null)
      val event = FacadeRequest(Uri("/rewritten-resource/100501"), "feed:put", Map.empty,
        ObjV("fullName" → "John Smith", "userName" → "jsmith", "password" → "neverforget")
      )
      val context = mockContext(request)
      val requestFilters = filterChain.findRequestFilters(ContextWithRequest(context, request))
      val eventFilters = filterChain.findEventFilters(mockContext(request), event)

      requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter shouldBe a[RewriteRequestFilter]
      eventFilters.head.asInstanceOf[ConditionalEventFilterProxy].filter shouldBe a[RewriteEventFilter]
    }

    "rewrite filters. forward request filters, inverted event filters with patterns" in {
      val request = FacadeRequest(Uri("/test-rewrite-method"), "put", Map.empty, Null)
      val event = FacadeRequest(Uri("/revault/content/{path:*}", Map("path" → "some-service")), "feed:put", Map.empty, Null)
      val notMatchedEvent = FacadeRequest(Uri("/revault/content/{path:*}", Map("path" → "other-service")), "feed:put", Map.empty, Null)

      val context = mockContext(request)
      val requestFilters = filterChain.findRequestFilters(ContextWithRequest(context, request))
      val eventFilters = filterChain.findEventFilters(mockContext(request), event)
      val notMatchedEventFilters = filterChain.findEventFilters(mockContext(request), notMatchedEvent)

      requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter shouldBe a[RewriteRequestFilter]
      eventFilters.head.asInstanceOf[ConditionalEventFilterProxy].filter shouldBe a[RewriteEventFilter]
      notMatchedEventFilters.head.asInstanceOf[ConditionalEventFilterProxy].filter shouldBe a[RewriteEventFilter]  // we assign filter chain on templated URI, not on formatted one, so despite
                                                                  // formatted URI of this event doesn't match rewritten URI from RAML config,
                                                                  // rewrite filter will be assigned to this event, but will do nothing
    }
  }
}