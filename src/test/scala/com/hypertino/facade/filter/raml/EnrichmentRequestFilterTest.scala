package com.hypertino.facade.filter.raml

import com.hypertino.facade.FacadeConfigPaths
import com.hypertino.binders.value.{Obj, Text}
import com.hypertino.facade.filter.chain.FilterChain
import com.hypertino.facade.model.{ContextWithRequest, FacadeRequest}
import com.hypertino.facade.modules.TestInjectors
import com.hypertino.facade.raml._
import com.hypertino.facade.utils.FutureUtils
import com.hypertino.facade.workers.TestWsRestServiceApp
import com.hypertino.facade.TestBase
import com.hypertino.hyperbus.transport.api.uri.Uri
import com.hypertino.servicecontrol.api.Service
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.ExecutionContext.Implicits.global

class EnrichmentRequestFilterTest extends TestBase {

  System.setProperty(FacadeConfigPaths.RAML_FILE, "raml-configs/enrich-request-filter-test.raml")
  implicit val injector = TestInjectors()
  val ramlFilters = inject[FilterChain]("ramlFilterChain")
  val app = inject[Service].asInstanceOf[TestWsRestServiceApp]

  "EnrichmentFilter" - {
    "add fields if request headers are present" in {
      val filters = Seq(
        new EnrichRequestFilter(Field("clientIp", DataType.DEFAULT_TYPE_NAME, Seq(EnrichAnnotation(RamlAnnotation.CLIENT_IP, None)))),
        new EnrichRequestFilter(Field("acceptLanguage", DataType.DEFAULT_TYPE_NAME, Seq(EnrichAnnotation(RamlAnnotation.CLIENT_LANGUAGE, None)))))

      val request = FacadeRequest(
        Uri("/resource"),
        Method.POST,
        Map("Accept-Language" → Seq("ru")),
        Map("field" → Text("value"))
      )

      val requestContext = mockContext(request)
      val filterChain = FutureUtils.chain(ContextWithRequest(requestContext, request), filters.map(f ⇒ f.apply(_)))
      whenReady(filterChain, Timeout(Span(10, Seconds))) { filteredCWR ⇒
        val expectedRequest = FacadeRequest(
          Uri("/resource"),
          Method.POST,
          Map.empty,
          Map("field" → Text("value"),
            "clientIp" → Text("127.0.0.1"),
            "acceptLanguage" → Text("ru")))
        filteredCWR.request.copy(headers=Map.empty) shouldBe expectedRequest
      }
    }

    "don't add fields if request headers are missed" in {
      val filter = new EnrichRequestFilter(Field("acceptLanguage", DataType.DEFAULT_TYPE_NAME, Seq(EnrichAnnotation(RamlAnnotation.CLIENT_LANGUAGE, None))))

      val initialRequest = FacadeRequest(
        Uri("/resource"),
        Method.POST,
        Map.empty,
        Map("field" → Text("value"))
      )
      val requestContext = mockContext(initialRequest)

      whenReady(filter.apply(ContextWithRequest(requestContext, initialRequest)), Timeout(Span(10, Seconds))) { filteredCWR ⇒
        filteredCWR.request shouldBe initialRequest
      }
    }

    "nested fields" in {
      val request = FacadeRequest(Uri("/complex-resource"), "post", Map.empty,
        Obj(Map("value" → Obj(
                  Map("publicField" → Text("new value"))
              )
            )
        )
      )
      val context = mockContext(request)
      val enrichedRequest = ramlFilters.filterRequest(ContextWithRequest(context, request)).futureValue.request
      val fields = enrichedRequest.body.asMap
      val valueSubFields = fields("value").asMap
      valueSubFields("address") shouldBe Text("127.0.0.1")
      valueSubFields("publicField") shouldBe Text("new value")
    }
  }
}
