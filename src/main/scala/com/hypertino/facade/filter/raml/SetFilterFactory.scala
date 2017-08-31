package com.hypertino.facade.filter.raml

import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.raml._
import com.hypertino.hyperbus.model.HRL
import com.typesafe.config.Config
import scaldi.Injectable

class SetFilterFactory(config: Config, protected val predicateEvaluator: ExpressionEvaluator) extends RamlFilterFactory with Injectable {
  override def createFilters(target: RamlFilterTarget): SimpleFilterChain = {
    val set = target match {
      case ResourceTarget(uri, set: SetAnnotation) ⇒ set
      case MethodTarget(uri, method, set: SetAnnotation) ⇒ set
      case otherTarget ⇒ throw RamlConfigException(s"Annotation 'set' cannot be assigned to $otherTarget")
    }
    SimpleFilterChain(
      requestFilters = Seq(new SetRequestFilter(set, predicateEvaluator)),
      responseFilters = Seq.empty,
      eventFilters = Seq()
    )
  }
}
