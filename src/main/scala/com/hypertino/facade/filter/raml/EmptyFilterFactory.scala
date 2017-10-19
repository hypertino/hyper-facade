package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.chain.{FilterChain, SimpleFilterChain}
import com.hypertino.facade.filter.model.{RamlFilterFactory, RamlFilterTarget}
import com.hypertino.facade.filter.parser.ExpressionEvaluator

class EmptyFilterFactory(protected val predicateEvaluator: ExpressionEvaluator) extends RamlFilterFactory {
  override def createFilters(target: RamlFilterTarget): SimpleFilterChain = {
    FilterChain.empty
  }

  override def createRamlAnnotation(name: String, value: Value) = ???
}
