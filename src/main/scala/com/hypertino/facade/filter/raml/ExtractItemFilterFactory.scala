package com.hypertino.facade.filter.raml

import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.raml._
import com.hypertino.hyperbus.model.HRL
import com.typesafe.config.Config
import scaldi.Injectable

class ExtractItemFilterFactory(protected val predicateEvaluator: ExpressionEvaluator) extends RamlFilterFactory with Injectable {
  override def createFilters(target: RamlFilterTarget): SimpleFilterChain = {
    target match {
      case ResourceTarget(_, ExtractItemAnnotation(_, _)) ⇒
      case MethodTarget(_, _, ExtractItemAnnotation(_, _)) ⇒
      case otherTarget ⇒ throw RamlConfigException(s"Annotation 'extract_item' cannot be assigned to $otherTarget")
    }
    SimpleFilterChain(
      requestFilters = Seq(new ExtractItemRequestFilter(predicateEvaluator)),
      responseFilters = Seq.empty,
      eventFilters = Seq.empty
    )
  }
}
