package com.hypertino.facade.raml

import com.hypertino.facade.filter.model.ConditionalRequestFilterProxy
import com.hypertino.facade.filter.raml.{RequestFieldFilterAdapter, ResponseFieldFilterAdapter, RewriteRequestFilter}
import com.hypertino.facade.TestBase
import com.hypertino.hyperbus.model

class RamlConfigurationBuilderTest extends TestBase(ramlConfigFiles=Seq("raml-config-parser-test.raml")) {
  private val ramlConfig = inject[RamlConfiguration]

  "Request filters" should "be empty if no annotation is applied" in {
    val statusFilterChain = ramlConfig
      .resourcesByPattern("/without-annotations")
      .methods(Method(model.Method.POST))
      .requests
      .ramlContentTypes(None)
      .filterChain

    statusFilterChain.requestFilters shouldBe Seq.empty
  }

  it should "have filters including field filters if annotations are applied" in {
    val statusServiceFilterChain = ramlConfig
      .resourcesByPattern("/request-annotations")
      .methods(Method(model.Method.POST))
      .requests
      .ramlContentTypes(Some(ContentType("test-request")))
      .filterChain

    statusServiceFilterChain
      .requestFilters
      .size shouldBe 2

    statusServiceFilterChain
      .requestFilters(0) shouldBe a[RequestFieldFilterAdapter]

    val rffa = statusServiceFilterChain
      .requestFilters(0).asInstanceOf[RequestFieldFilterAdapter]

    rffa.typeDef.typeName shouldBe "abc"
//    rffa.fields.size shouldBe 2
//    rffa.fields(0).field.name shouldBe "clientIp"
//    rffa.fields(0).field.annotations.head shouldBe a[SetAnnotation]
//
//    rffa.fields(1).field.name shouldBe "password"
//    rffa.fields(1).field.annotations.head shouldBe a[RemoveAnnotation]
//
//    statusServiceFilterChain
//      .requestFilters(1)
//      .asInstanceOf[ConditionalRequestFilterProxy]
//      .filter shouldBe a[RewriteRequestFilter]
  }

  /*
  it should "have filters including inner field filters if annotations are applied" in {
    val filterChain = ramlConfig
      .resourcesByPattern("/request-inner-annotations")
      .methods(Method(model.Method.POST))
      .requests
      .ramlContentTypes(Some(ContentType("test-request-with-inner-fields")))
      .filterChain

    filterChain
      .requestFilters
      .size shouldBe 2

    filterChain
      .requestFilters(0) shouldBe a[RequestFieldFilterAdapter]

    val rffa = filterChain
      .requestFilters(0).asInstanceOf[RequestFieldFilterAdapter]

    rffa.fields.size shouldBe 2
    rffa.fields(0).field.name shouldBe "password"
    rffa.fields(0).field.annotations.head shouldBe a[RemoveAnnotation]

    rffa.fields(1).field.name shouldBe "`inner`.secret"
    rffa.fields(1).field.annotations.head shouldBe a[RemoveAnnotation]

    filterChain
      .requestFilters(1)
      .asInstanceOf[ConditionalRequestFilterProxy]
      .filter shouldBe a[RewriteRequestFilter]
  }
*/

//
//  "response filters" in {
//    val usersFilterChain = ramlConfig.resourcesByUri("/status").methods(Method(GET)).responses(200).ramlContentTypes(None).filters
//    usersFilterChain.responseFilters.head.asInstanceOf[ConditionalResponseFilterProxy].filter shouldBe a[DenyResponseFilter]
//    usersFilterChain.eventFilters.head.asInstanceOf[ConditionalEventFilterProxy].filter shouldBe a[DenyEventFilter]
//  }
//
//  "request filters by contentType" in {
//    val resourceStateContentType = Some("app-server-status")
//    val resourceUpdateContentType = Some("app-server-status-update")
//
//    val resourceStateFilters = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.ramlContentTypes(resourceStateContentType.map(ContentType)).filters
//    resourceStateFilters.requestFilters shouldBe Seq.empty
//    val resourceUpdateFilters = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.ramlContentTypes(resourceUpdateContentType.map(ContentType)).filters
//    resourceUpdateFilters.requestFilters shouldBe Seq.empty
//    val defaultFilters = ramlConfig.resourcesByUri("/reliable-feed/{content:*}").methods(Method(POST)).requests.ramlContentTypes(None).filters
//    defaultFilters.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter shouldBe a[EnrichRequestFilter]
//  }
//
//  "annotations on nested fields" in {
//    val responseFilterChain = ramlConfig.resourcesByUri("/complex-resource").methods(Method(POST)).responses(200).ramlContentTypes(None).filters
//    responseFilterChain.responseFilters.head.asInstanceOf[ConditionalResponseFilterProxy].filter shouldBe a[DenyResponseFilter]
//
//    val requestFilterChain = ramlConfig.resourcesByUri("/complex-resource").methods(Method(POST)).requests.ramlContentTypes(None).filters
//    requestFilterChain.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter shouldBe a[EnrichRequestFilter]
//  }
//
//  "annotations on parent resource" in {
//    val parentRewriteFilter = ramlConfig.resourcesByUri("/parent").filters.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter
//    parentRewriteFilter shouldBe a[RewriteRequestFilter]
//    parentRewriteFilter.asInstanceOf[RewriteRequestFilter].uri shouldBe "/revault/content/some-service"
//
//    val childRewriteFilter = ramlConfig.resourcesByUri("/parent/child").filters.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter
//    childRewriteFilter shouldBe a[RewriteRequestFilter]
//    childRewriteFilter.asInstanceOf[RewriteRequestFilter].uri shouldBe "/revault/content/some-service/child"
//  }
//
//  "annotations on external child resource" in {
//    val childRewriteFilters = ramlConfig.resourcesByUri("/parent/external-child").filters.requestFilters
//    childRewriteFilters shouldBe empty
//  }
//
//  "request URI substitution" in {
//    val parameterRegularMatch = ramlReader.resourceUri(Uri("/unreliable-feed/someContent"), "get")
//    parameterRegularMatch shouldBe Uri("/unreliable-feed/{content}", Map("content" → "someContent"))
//
//    val strictUriMatch = ramlReader.resourceUri(Uri("/unreliable-feed/someContent/someDetails"), "get")
//    strictUriMatch shouldBe Uri("/unreliable-feed/someContent/someDetails")
//
//    val parameterPathMatch = ramlReader.resourceUri(Uri("/reliable-feed/someContent/someDetails"), "get")
//    parameterPathMatch shouldBe Uri("/reliable-feed/{content:*}", Map("content" → "someContent/someDetails"))
//
//    val parameterArgPathMatch = ramlReader.resourceUri(Uri("/reliable-feed/someContent/{arg}", Map("arg" → "someDetails")), "get")
//    parameterArgPathMatch shouldBe Uri("/reliable-feed/{content:*}", Map("content" → "someContent/someDetails"))
//  }
//
//  "filter (annotation) with arguments" in {
//    val rs0 = ramlConfig.resourcesByUri("/test-rewrite-method")
//    rs0.filters.requestFilters shouldBe empty
//
//    val rs1 = ramlConfig.resourcesByUri("/test-rewrite-method").methods(Method(PUT))
//    rs1.methodFilters.requestFilters.head.asInstanceOf[ConditionalRequestFilterProxy].filter shouldBe a[RewriteRequestFilter]
//  }
//
//  "type inheritance" in {
//    val extStatusTypeDef = ramlConfig.resourcesByUri("/ext-status").methods(Method(POST)).requests.ramlContentTypes(None).typeDefinition
//    val fields = extStatusTypeDef.fields
//    fields.size shouldBe 3
//    assert(fields.exists(_.name == "statusCode"))
//    assert(fields.exists(_.name == "processedBy"))
//    assert(fields.exists(_.name == "timestamp"))
//  }
}