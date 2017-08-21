package com.hypertino.facade.filter.raml

import com.hypertino.facade.filter.chain.{FilterChain, SimpleFilterChain}
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.raml.DenyAnnotation
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

class DenyFilterFactory(protected val predicateEvaluator: ExpressionEvaluator) extends RamlFilterFactory with Injectable {
  private val log = LoggerFactory.getLogger(getClass)

  override def createFilters(target: RamlFilterTarget): SimpleFilterChain = {
    target match {
      case ResourceTarget(_, DenyAnnotation(_, _, _)) ⇒
        SimpleFilterChain(
          requestFilters = Seq(new DenyRequestFilter(predicateEvaluator)),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )

      case MethodTarget(_, _, DenyAnnotation(_, _, _)) ⇒
        SimpleFilterChain(
          requestFilters = Seq(new DenyRequestFilter(predicateEvaluator)),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )

      case unknownTarget ⇒
        log.warn(s"Annotation (deny) is not supported for target $unknownTarget. Empty filter chain will be created")
        FilterChain.empty
    }
  }
}
