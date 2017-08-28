package com.hypertino.facade.filter.raml

import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.raml._
import com.hypertino.hyperbus.model.HRL
import com.typesafe.config.Config
import scaldi.Injectable

class ForwardFilterFactory(config: Config, protected val predicateEvaluator: ExpressionEvaluator) extends RamlFilterFactory with Injectable {
  override def createFilters(target: RamlFilterTarget): SimpleFilterChain = {
    val (sourceLocation, ramlMethod, destinationLocation, query) = target match {
      case ResourceTarget(uri, ForwardAnnotation(_, _, l, q)) ⇒ (uri, None, l, q)
      case MethodTarget(uri, method, ForwardAnnotation(_, _, l, q)) ⇒ (uri, Some(Method(method)), l, q)
      case otherTarget ⇒ throw RamlConfigException(s"Annotation 'forward' cannot be assigned to $otherTarget")
    }
    val sourceHRL = HRL(sourceLocation)
    SimpleFilterChain(
      requestFilters = Seq(new ForwardRequestFilter(sourceHRL, destinationLocation, query, predicateEvaluator)),
      responseFilters = Seq.empty,
      eventFilters = Seq.empty
    )
  }
}
