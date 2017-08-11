package com.hypertino.facade.filter.raml

import com.typesafe.config.Config
import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.raml._
import com.hypertino.hyperbus.model.HRL
import scaldi.{Injectable, Injector}

class RewriteFilterFactory(config: Config, protected val predicateEvaluator: ExpressionEvaluator) extends RamlFilterFactory with Injectable {
  override def createFilters(target: RamlFilterTarget): SimpleFilterChain = {
    val (sourceLocation, ramlMethod, destinationLocation, query) = target match {
      case ResourceTarget(uri, RewriteAnnotation(_, _, l, q)) ⇒ (uri, None, l, q)
      case MethodTarget(uri, method, RewriteAnnotation(_, _, l, q)) ⇒ (uri, Some(Method(method)), l, q)
      case otherTarget ⇒ throw RamlConfigException(s"Annotation 'rewrite' cannot be assigned to $otherTarget")
    }
    val sourceHRL = HRL(sourceLocation)
    val destinationHRL = HRL(destinationLocation, query)
    RewriteIndexHolder.updateRewriteIndex(sourceHRL, destinationHRL, ramlMethod)
    SimpleFilterChain(
      requestFilters = Seq(new RewriteRequestFilter(sourceHRL, destinationHRL, predicateEvaluator)),
      responseFilters = Seq.empty,
      eventFilters = Seq(new RewriteEventFilter(predicateEvaluator))
    )
  }
}
