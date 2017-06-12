package com.hypertino.facade.raml

import com.hypertino.facade.FacadeConfigPaths
import com.hypertino.facade.filter.model.{ConditionalEventFilterProxy, ConditionalRequestFilterProxy, ConditionalResponseFilterProxy}
import com.hypertino.facade.filter.raml._
import com.hypertino.facade.modules.TestInjectors
import com.hypertino.facade.raml.Method._
import com.hypertino.facade.workers.TestWsRestServiceApp
import com.hypertino.facade.TestBase
import com.hypertino.hyperbus.transport.api.uri.Uri
import com.hypertino.servicecontrol.api.Service

class RamlConfigurationBuilderTest extends TestBase {
  System.setProperty(FacadeConfigPaths.RAML_FILE, "raml-configs/raml-config-parser-test.raml")
  implicit val injector = TestInjectors()
  val ramlConfig = inject[RamlConfiguration]
  val ramlReader = inject[RamlConfigurationReader]
  val app = inject[Service].asInstanceOf[TestWsRestServiceApp]

  "RamlConfig" - {
    "request filters" in {
      val statusFilterChain = ramlConfig.resourcesByUri("/status").methods(Method(POST)).requests.ramlContentTypes(None).filters
      statusFilterChain.requestFilters shouldBe Seq.empty

      val statusServiceFilterChain = ramlConfig.resourcesByUri("/status/test-service").methods(Method(GET)).requests.ramlContentTypes(None).filters
      statusServiceFilterChain.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter shouldBe a[EnrichRequestFilter]
    }

    "response filters" in {
      val usersFilterChain = ramlConfig.resourcesByUri("/status").methods(Method(GET)).responses(200).ramlContentTypes(None).filters
      usersFilterChain.responseFilters.head.asInstanceOf[ConditionalResponseFilterProxy].filter shouldBe a[DenyResponseFilter]
      usersFilterChain.eventFilters.head.asInstanceOf[ConditionalEventFilterProxy].filter shouldBe a[DenyEventFilter]
    }

    "request filters by contentType" in {
      val resourceStateContentType = Some("app-server-status")
      val resourceUpdateContentType = Some("app-server-status-update")

      val resourceStateFilters = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.ramlContentTypes(resourceStateContentType.map(ContentType)).filters
      resourceStateFilters.requestFilters shouldBe Seq.empty
      val resourceUpdateFilters = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.ramlContentTypes(resourceUpdateContentType.map(ContentType)).filters
      resourceUpdateFilters.requestFilters shouldBe Seq.empty
      val defaultFilters = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.ramlContentTypes(None).filters
      defaultFilters.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter shouldBe a[EnrichRequestFilter]
    }

    "annotations on nested fields" in {
      val responseFilterChain = ramlConfig.resourcesByUri("/complex-resource").methods(Method(POST)).responses(200).ramlContentTypes(None).filters
      responseFilterChain.responseFilters.head.asInstanceOf[ConditionalResponseFilterProxy].filter shouldBe a[DenyResponseFilter]

      val requestFilterChain = ramlConfig.resourcesByUri("/complex-resource").methods(Method(POST)).requests.ramlContentTypes(None).filters
      requestFilterChain.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter shouldBe a[EnrichRequestFilter]
    }

    "annotations on parent resource" in {
      val parentRewriteFilter = ramlConfig.resourcesByUri("/parent").filters.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter
      parentRewriteFilter shouldBe a[RewriteRequestFilter]
      parentRewriteFilter.asInstanceOf[RewriteRequestFilter].uri shouldBe "/revault/content/some-service"

      val childRewriteFilter = ramlConfig.resourcesByUri("/parent/child").filters.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter
      childRewriteFilter shouldBe a[RewriteRequestFilter]
      childRewriteFilter.asInstanceOf[RewriteRequestFilter].uri shouldBe "/revault/content/some-service/child"
    }

    "annotations on external child resource" in {
      val childRewriteFilters = ramlConfig.resourcesByUri("/parent/external-child").filters.requestFilters
      childRewriteFilters shouldBe empty
    }

    "request URI substitution" in {
      val parameterRegularMatch = ramlReader.resourceUri(Uri("/unreliable-feed/someContent"), "get")
      parameterRegularMatch shouldBe Uri("/unreliable-feed/{content}", Map("content" → "someContent"))

      val strictUriMatch = ramlReader.resourceUri(Uri("/unreliable-feed/someContent/someDetails"), "get")
      strictUriMatch shouldBe Uri("/unreliable-feed/someContent/someDetails")

      val parameterPathMatch = ramlReader.resourceUri(Uri("/reliable-feed/someContent/someDetails"), "get")
      parameterPathMatch shouldBe Uri("/reliable-feed/{content:*}", Map("content" → "someContent/someDetails"))

      val parameterArgPathMatch = ramlReader.resourceUri(Uri("/reliable-feed/someContent/{arg}", Map("arg" → "someDetails")), "get")
      parameterArgPathMatch shouldBe Uri("/reliable-feed/{content:*}", Map("content" → "someContent/someDetails"))
    }

    "filter (annotation) with arguments" in {
      val rs0 = ramlConfig.resourcesByUri("/test-rewrite-method")
      rs0.filters.requestFilters shouldBe empty

      val rs1 = ramlConfig.resourcesByUri("/test-rewrite-method").methods(Method(PUT))
      rs1.methodFilters.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter shouldBe a[RewriteRequestFilter]
    }

    "type inheritance" in {
      val extStatusTypeDef = ramlConfig.resourcesByUri("/ext-status").methods(Method(POST)).requests.ramlContentTypes(None).typeDefinition
      val fields = extStatusTypeDef.fields
      fields.size shouldBe 3
      assert(fields.exists(_.name == "statusCode"))
      assert(fields.exists(_.name == "processedBy"))
      assert(fields.exists(_.name == "timestamp"))
    }
  }
}
