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

    rffa.typeDef.typeName shouldBe "abc"
  }
}