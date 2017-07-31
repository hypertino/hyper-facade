package com.hypertino.facade.raml

import com.hypertino.facade.TestBase
import com.hypertino.facade.filter.model.ConditionalRequestFilterProxy
import com.hypertino.facade.filter.raml.{RequestFieldFilterAdapter, ResponseFieldFilterAdapter, RewriteRequestFilter}
import com.hypertino.hyperbus.model

class RamlConfigurationBuilderCollectionTest extends TestBase(ramlConfigFiles=Seq("raml-collection-config-parser-test.raml")) {
  private val ramlConfig = inject[RamlConfiguration]

  "Responses" should "have filters on collection fields" in {
    val filterChain = ramlConfig
      .resourcesByPattern("/request-collection-annotations")
      .methods(Method(model.Method.GET))
      .responses
      .head
      ._2
      .ramlContentTypes
      .head
      ._2
      .filterChain

    filterChain
      .responseFilters
      .size shouldBe 1

    val rffa = filterChain
      .responseFilters(0)
      .asInstanceOf[ResponseFieldFilterAdapter]

    rffa.fields.size shouldBe 1
    rffa.fields(0).field.name shouldBe "`collection[]`.secret"
    rffa.fields(0).field.annotations.head shouldBe a[RemoveAnnotation]
  }

  "Responses" should "have filters on top-level collection fields" in {
    val filterChain = ramlConfig
      .resourcesByPattern("/request-top-collection-annotations")
      .methods(Method(model.Method.GET))
      .responses
      .head
      ._2
      .ramlContentTypes
      .head
      ._2
      .filterChain

    filterChain
      .responseFilters
      .size shouldBe 1

    val rffa = filterChain
      .responseFilters(0)
      .asInstanceOf[ResponseFieldFilterAdapter]

    rffa.fields.size shouldBe 1
    rffa.fields(0).field.name shouldBe "`[]`.secret"
    rffa.fields(0).field.annotations.head shouldBe a[RemoveAnnotation]
  }

  "Responses" should "have filters on top-level and nested collection fields" in {
    val filterChain = ramlConfig
      .resourcesByPattern("/request-nested-collection-annotations")
      .methods(Method(model.Method.GET))
      .responses
      .head
      ._2
      .ramlContentTypes
      .head
      ._2
      .filterChain

    filterChain
      .responseFilters
      .size shouldBe 1

    val rffa = filterChain
      .responseFilters(0)
      .asInstanceOf[ResponseFieldFilterAdapter]

    rffa.fields.size shouldBe 1
    rffa.fields(0).field.name shouldBe "`[]`.`collectionTop[]`.`collection[]`.secret"
    rffa.fields(0).field.annotations.head shouldBe a[RemoveAnnotation]
  }
}