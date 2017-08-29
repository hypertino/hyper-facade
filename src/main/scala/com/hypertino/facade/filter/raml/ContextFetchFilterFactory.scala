package com.hypertino.facade.filter.raml

import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.raml._
import com.hypertino.hyperbus.Hyperbus
import monix.execution.Scheduler
import scaldi.Injectable

class ContextFetchFilterFactory(hyperbus: Hyperbus,
                                protected val predicateEvaluator: ExpressionEvaluator,
                                protected implicit val scheduler: Scheduler) extends RamlFilterFactory with Injectable {
  override def createFilters(target: RamlFilterTarget): SimpleFilterChain = {
    val fa = target match {
      case ResourceTarget(_, fa : ContextFetchAnnotation) ⇒ fa
      case MethodTarget(_, _, fa : ContextFetchAnnotation) ⇒ fa
      case otherTarget ⇒ throw RamlConfigException(s"Annotation 'context_fetch' cannot be assigned to $otherTarget")
    }
    SimpleFilterChain(
      requestFilters = Seq(new ContextFetchRequestFilter(fa, hyperbus, predicateEvaluator,scheduler)),
      responseFilters = Seq.empty,
      eventFilters = Seq.empty
    )
  }
}
