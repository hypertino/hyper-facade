package com.hypertino.facade.filter.raml

import com.hypertino.facade.filter.chain.{FilterChain, SimpleFilterChain}
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.PredicateEvaluator
import com.hypertino.facade.raml.DenyAnnotation
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

class PrivateFilterFactory(protected val predicateEvaluator: PredicateEvaluator) extends RamlFilterFactory {
  private val log = LoggerFactory.getLogger(getClass)

  override def createFilters(target: RamlTarget): SimpleFilterChain = {
    target match {
      case TargetField(_, field) ⇒
        SimpleFilterChain(
          requestFilters = Seq.empty,
          responseFilters = Seq(new PrivateResponseFilter(field, predicateEvaluator)),
          eventFilters = Seq(new PrivateEventFilter(field, predicateEvaluator))
        )

      case unknownTarget ⇒
        log.warn(s"Annotation (private) is not supported for target $unknownTarget. Empty filter chain will be created")
        FilterChain.empty
    }
  }
}
