package com.hypertino.facade.filter.raml

import com.hypertino.binders.annotations.fieldName
import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, PreparedExpression}
import com.hypertino.facade.raml._
import scaldi.Injectable

case class ExtractItemAnnotation(
                                  @fieldName("if") predicate: Option[PreparedExpression]
                                ) extends RamlAnnotation {
  def name: String = "extract_item"
}

class ExtractItemFilterFactory(protected val predicateEvaluator: ExpressionEvaluator) extends RamlFilterFactory with Injectable {
  override def createFilters(target: RamlFilterTarget): SimpleFilterChain = {
    target match {
      case ResourceTarget(_, _: ExtractItemAnnotation) ⇒
      case MethodTarget(_, _, _: ExtractItemAnnotation) ⇒
      case otherTarget ⇒ throw RamlConfigException(s"Annotation 'extract_item' cannot be assigned to $otherTarget")
    }
    SimpleFilterChain(
      requestFilters = Seq.empty,
      responseFilters = Seq(new ExtractItemResponseFilter(predicateEvaluator)),
      eventFilters = Seq.empty
    )
  }

  override def createRamlAnnotation(name: String, value: Value): RamlAnnotation = {
    import com.hypertino.hyperbus.serialization.SerializationOptions._
    import PreparedExpression._
    value.to[ExtractItemAnnotation]
  }
}
