package com.hypertino.facade.raml

import com.hypertino.facade.filters.annotated.ResponseFieldFilterAdapter
import com.hypertino.facade.{TestBase, TestBaseWithHyperbus}
import com.hypertino.hyperbus.model

class RamlConfigurationBuilderCollectionTest extends TestBaseWithHyperbus(ramlConfigFiles=Seq("raml-collection-config-parser-test.raml")) {
  import testServices._

  "Responses" should "have filters on collection fields" in {
    val filterChain = originalRamlConfig
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
      .responseFilters(0) shouldBe a[ResponseFieldFilterAdapter]
  }
}