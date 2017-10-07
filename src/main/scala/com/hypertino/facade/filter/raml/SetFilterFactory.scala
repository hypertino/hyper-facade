package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, PreparedExpression}
import com.hypertino.facade.raml._
import com.typesafe.config.Config
import scaldi.Injectable

case class SetAnnotation(
                          predicate: Option[PreparedExpression],
                          source: PreparedExpression,
                          target: Option[String]
                        ) extends RamlAnnotation {
  def name: String = "set"
}

class SetFilterFactory(config: Config, protected val predicateEvaluator: ExpressionEvaluator) extends RamlFilterFactory with Injectable {
  override def createFilters(target: RamlFilterTarget): SimpleFilterChain = {
    val set = target match {
      case ResourceTarget(_, set: SetAnnotation) ⇒ set
      case MethodTarget(_, _, set: SetAnnotation) ⇒ set
      case otherTarget ⇒ throw RamlConfigException(s"Annotation 'set' cannot be assigned to $otherTarget")
    }
    SimpleFilterChain(
      requestFilters = Seq(new SetRequestFilter(set, predicateEvaluator)),
      responseFilters = Seq.empty,
      eventFilters = Seq()
    )
  }

  override def createRamlAnnotation(name: String, value: Value): RamlAnnotation = {
    import PreparedExpression._
    import com.hypertino.hyperbus.serialization.SerializationOptions._
    value.to[SetAnnotation]
  }
}
