/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.raml

import com.hypertino.facade.filter.model.ConditionalRequestFilterProxy
import com.hypertino.facade.filter.raml._
import com.hypertino.facade.filters.annotated._
import com.hypertino.facade.{TestBase, TestBaseWithHyperbus}
import com.hypertino.hyperbus.model

class RamlConfigurationBuilderTest extends TestBaseWithHyperbus(ramlConfigFiles = Seq("raml-config-parser-test.raml")) {

  import testServices._

  "Request filters" should "be empty if no annotation is applied" in {
    val statusFilterChain = originalRamlConfig
      .resourcesByPattern("/without-annotations")
      .methods(Method(model.Method.POST))
      .requests
      .ramlContentTypes(None)
      .filterChain

    statusFilterChain.requestFilters shouldBe Seq.empty
  }

  it should "have filters including field filters if annotations are applied" in {
    val statusServiceFilterChain = originalRamlConfig
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

    rffa.typeDef.typeName shouldBe "TestRequest"
    val fa = rffa.typeDef.fields.values.filter(_.annotations.nonEmpty).toSeq

    fa.size shouldBe 2
    fa.head.fieldName shouldBe "clientIp"
    fa.head.annotations.map(_.annotation).head shouldBe a[SetFieldAnnotation]
    fa.head.annotations.map(_.filter).head shouldBe a[SetFieldFilter]
    fa(1).annotations.map(_.annotation).head shouldBe a[RemoveFieldAnnotation]
    fa(1).annotations.map(_.filter).head shouldBe RemoveFieldFilter

    statusServiceFilterChain
      .requestFilters(1)
      .asInstanceOf[ConditionalRequestFilterProxy]
      .filter shouldBe a[RewriteRequestFilter]
  }

  it should "have filters on data types" in {
    val filterChain = originalRamlConfig
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

    val fa = rffa.typeDef.fields.values.filter(_.annotations.nonEmpty).toSeq

    fa.size shouldBe 2
    fa(0).fieldName shouldBe "password"
    fa(0).annotations.map(_.annotation).head shouldBe a[RemoveFieldAnnotation]
    fa(0).annotations.map(_.filter).head shouldBe RemoveFieldFilter

    fa(1).fieldName shouldBe "fetched"
    fa(1).annotations.map(_.annotation).head shouldBe a[FetchFieldAnnotation]
    val ffa = fa(1).annotations.map(_.annotation).head.asInstanceOf[FetchFieldAnnotation]
    ffa.onError shouldBe "remove"

    rffa.typeDef.fields.keySet should contain("inner")
    val inner = rffa.typeDef.fields("inner")
    inner.fieldTypeName shouldBe "TestInnerFields"

    val typeDef = originalRamlConfig.dataTypes("TestInnerFields")
    typeDef.fields.size shouldBe 2
    typeDef.fields("secret").annotations.size shouldBe 1

    filterChain
      .requestFilters(1)
      .asInstanceOf[ConditionalRequestFilterProxy]
      .filter shouldBe a[RewriteRequestFilter]
  }

  it should "have multiple filters defined as list" in {
    val ma = originalRamlConfig
      .resourcesByPattern("/multiple-annotations")
      .annotations

    ma.size shouldBe 5
    ma(0) shouldBe a[RewriteAnnotation]
    ma(1) shouldBe a[RewriteAnnotation]
    ma(2) shouldBe a[ErrorResponseAnnotation]
    ma(3) shouldBe a[ErrorResponseAnnotation]
    ma(4) shouldBe a[ContextFetchAnnotation]

    ma(4).asInstanceOf[ContextFetchAnnotation].onError shouldBe "remove"
  }

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