package com.hypertino.facade.filter.raml

import com.hypertino.facade.filter.chain.{FilterChain, SimpleFilterChain}
import com.hypertino.facade.filter.model.{RamlFilterFactory, RamlTarget}
import com.hypertino.facade.filter.parser.ExpressionEvaluator

class EmptyFilterFactory(protected val predicateEvaluator: ExpressionEvaluator) extends RamlFilterFactory {
  override def createFilters(target: RamlTarget): SimpleFilterChain = {
    FilterChain.empty
  }
}
