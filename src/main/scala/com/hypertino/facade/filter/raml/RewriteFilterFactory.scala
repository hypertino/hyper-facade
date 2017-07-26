package com.hypertino.facade.filter.raml

import com.typesafe.config.Config
import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.raml._
import com.hypertino.hyperbus.model.HRL
import scaldi.{Injectable, Injector}

class RewriteFilterFactory(config: Config, protected val predicateEvaluator: ExpressionEvaluator) extends RamlFilterFactory with Injectable {
  override def createFilters(target: RamlTarget): SimpleFilterChain = {
    val (rewrittenUri, originalUri, ramlMethod) = target match {
      case TargetResource(uri, RewriteAnnotation(_, _, newUri)) ⇒ (newUri, uri, None)
      case TargetMethod(uri, method, RewriteAnnotation(_, _, newUri)) ⇒ (newUri, uri, Some(Method(method)))
      case otherTarget ⇒ throw RamlConfigException(s"Annotation 'rewrite' cannot be assigned to $otherTarget")
    }
    RewriteIndexHolder.updateRewriteIndex(HRL(originalUri), HRL(rewrittenUri), ramlMethod)
    SimpleFilterChain(
      requestFilters = Seq(new RewriteRequestFilter(rewrittenUri, predicateEvaluator)),
      responseFilters = Seq.empty,
      eventFilters = Seq(new RewriteEventFilter(predicateEvaluator))
    )
  }
}
