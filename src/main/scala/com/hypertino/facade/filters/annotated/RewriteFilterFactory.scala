package com.hypertino.facade.filters.annotated

import com.hypertino.binders.annotations.fieldName
import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, PreparedExpression}
import com.hypertino.facade.raml._
import com.hypertino.hyperbus.model.HRL
import com.typesafe.config.Config
import scaldi.Injectable

case class RewriteAnnotation(
                              @fieldName("if") predicate: Option[PreparedExpression],
                              location: String,
                              query: Value
                            ) extends RamlAnnotation {
  def name: String = "rewrite"
}


class RewriteFilterFactory(config: Config, protected val predicateEvaluator: ExpressionEvaluator) extends RamlFilterFactory with Injectable {
  override def createFilters(target: RamlFilterTarget): SimpleFilterChain = {
    val (sourceLocation, ramlMethod, destinationLocation, query) = target match {
      case ResourceTarget(uri, RewriteAnnotation(_, l, q)) ⇒ (uri, None, l, q)
      case MethodTarget(uri, method, RewriteAnnotation(_, l, q)) ⇒ (uri, Some(Method(method)), l, q)
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

  override def createRamlAnnotation(name: String, value: Value): RamlAnnotation = {
    value.to[RewriteAnnotation]
  }
}
